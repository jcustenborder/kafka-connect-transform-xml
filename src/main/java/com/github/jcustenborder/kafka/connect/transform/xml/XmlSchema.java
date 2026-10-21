/**
 * Copyright © 2026 Jeremy Custenborder (jcustenborder@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jcustenborder.kafka.connect.transform.xml;

import com.sun.xml.xsom.XSAttributeUse;
import com.sun.xml.xsom.XSComplexType;
import com.sun.xml.xsom.XSDeclaration;
import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSFacet;
import com.sun.xml.xsom.XSModelGroup;
import com.sun.xml.xsom.XSParticle;
import com.sun.xml.xsom.XSSchemaSet;
import com.sun.xml.xsom.XSSimpleType;
import com.sun.xml.xsom.XSTerm;
import com.sun.xml.xsom.XSType;
import com.sun.xml.xsom.parser.XSOMParser;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.data.ConnectSchema;
import org.apache.kafka.connect.data.Decimal;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.errors.DataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Configuration-time XSD interpretation and per-document validated conversion. */
final class XmlSchema {
  private static final Logger log = LoggerFactory.getLogger(XmlSchema.class);
  private final String prefix;
  private final Map<QName, XmlType> roots = new HashMap<>();
  private final Map<XSType, XmlType> types = new IdentityHashMap<>();
  private final Set<XSType> visiting = Collections.newSetFromMap(new IdentityHashMap<>());
  private final Map<String, XSType> names = new HashMap<>();
  private final javax.xml.validation.Schema validation;

  XmlSchema(FromXmlConfig config) {
    prefix = config.schemaPackage;
    try {
      SchemaSources sources = new SchemaSources(config.schemaUrls);
      SAXParserFactory sax = SAXParserFactory.newDefaultInstance();
      sax.setNamespaceAware(true);
      sax.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      sax.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      sax.setFeature("http://xml.org/sax/features/external-general-entities", false);
      sax.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      XSOMParser parser = new XSOMParser(sax);
      parser.setEntityResolver(sources);
      parser.setErrorHandler(new ConnectErrorListener(log));
      for (URL url : config.schemaUrls) {
        parser.parse(sources.source(url));
      }
      XSSchemaSet model = parser.getResult();
      if (model == null) {
        throw new ConfigException("Cannot interpret the configured XSDs");
      }
      sources.complete(parser.getDocuments());
      SchemaFactory factory = SchemaFactory.newDefaultInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setResourceResolver(sources);
      factory.setErrorHandler(new ConnectErrorListener(log));
      validation = factory.newSchema(sources.validationSources());
      for (Iterator<XSElementDecl> elements = model.iterateElementDecls(); elements.hasNext();) {
        XSElementDecl element = elements.next();
        if (element.getType().isComplexType()) {
          checkSubstitution(element);
          XmlType type = type(element.getType(), prefix + "." + className(element.getName()), element.isNillable());
          roots.put(name(element), type);
        }
      }
      if (roots.isEmpty()) {
        throw new ConfigException("An XML-to-Struct schema requires at least one complex root element");
      }
      log.debug("Mapped {} XML roots and {} complex types to Connect schemas", roots.size(), types.size());
      types.clear();
      names.clear();
    } catch (IOException | SAXException | ParserConfigurationException | IllegalArgumentException e) {
      ConfigException error = new ConfigException("Cannot configure XSD mapping: " + e.getMessage());
      error.initCause(e);
      throw error;
    }
  }

  SchemaAndValue read(InputSource source) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
      factory.setNamespaceAware(true);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setSchema(validation);
      DocumentBuilder parser = factory.newDocumentBuilder();
      parser.setErrorHandler(new ConnectErrorListener(log));
      Element root = parser.parse(source).getDocumentElement();
      XmlType type = roots.get(XmlType.name(root));
      if (type == null) {
        throw new DataException("No Connect Struct mapping for XML root " + XmlType.name(root));
      }
      Object value = type.read(root);
      ConnectSchema.validateValue(type.schema, value);
      return new SchemaAndValue(type.schema, value);
    } catch (ParserConfigurationException e) {
      throw new ConnectException("Cannot initialize XML document parser", e);
    } catch (SAXException | IOException e) {
      throw new DataException("XML document does not match its configured XSD: " + e.getMessage(), e);
    }
  }

  private XmlType type(XSType declaration, String path, boolean optional) {
    if (declaration.isSimpleType()) {
      return scalar(declaration.asSimpleType(), optional);
    }
    XmlType cached = types.get(declaration);
    if (cached != null) {
      return cached;
    }
    if (!visiting.add(declaration)) {
      throw unsupported(declaration, "recursive types");
    }
    XSComplexType complex = declaration.asComplexType();
    if (complex.isAbstract()) {
      throw unsupported(declaration, "abstract complex types");
    }
    if (complex.isMixed() || complex.getContentType().asSimpleType() != null) {
      throw unsupported(declaration, "mixed or simple content");
    }
    if (complex.getAttributeWildcard() != null) {
      throw unsupported(declaration, "attribute wildcards");
    }
    String schemaName = complex.isAnonymous() ? path : prefix + "." + className(complex.getName());
    if (complex.isAnonymous() && complex.getScope() != null && complex.getScope().isGlobal()) {
      schemaName = prefix + "." + className(complex.getScope().getName());
    }
    XSType previous = names.putIfAbsent(schemaName, declaration);
    if (previous != null && previous != declaration) {
      throw unsupported(declaration, "ambiguous Connect schema name " + schemaName);
    }
    List<XmlType.Member> members = new ArrayList<>();
    XSParticle content = complex.getContentType().asParticle();
    if (content != null) {
      members(content, schemaName, false, members);
    }
    List<XSAttributeUse> attributes = new ArrayList<>(complex.getAttributeUses());
    attributes.sort(Comparator.comparingInt(attribute -> attribute.getLocator() == null ? 0 : attribute.getLocator().getLineNumber()));
    for (XSAttributeUse attribute : attributes) {
      XmlType type = scalar(attribute.getDecl().getType(), !attribute.isRequired());
      members.add(new XmlType.Member(name(attribute.getDecl()), type, true, false, type.schema));
    }
    SchemaBuilder builder = SchemaBuilder.struct().name(schemaName).optional();
    Set<String> fields = new HashSet<>();
    for (XmlType.Member member : members) {
      if (!fields.add(member.name().getLocalPart())) {
        throw unsupported(declaration, "ambiguous Connect field name " + member.name().getLocalPart());
      }
      builder.field(member.name().getLocalPart(), member.schema());
    }
    XmlType result = new XmlType(builder.build(), null, null, members);
    visiting.remove(declaration);
    types.put(declaration, result);
    return result;
  }

  private void members(XSParticle particle, String path, boolean optional, List<XmlType.Member> members) {
    optional |= particle.getMinOccurs().signum() == 0;
    XSTerm term = particle.getTerm();
    if (term.isElementDecl()) {
      XSElementDecl element = term.asElementDecl();
      checkSubstitution(element);
      XmlType type = type(element.getType(), path + "." + className(element.getName()), optional || element.isNillable());
      Schema schema = particle.isRepeated() ? SchemaBuilder.array(type.schema).optional().build() : type.schema;
      members.add(new XmlType.Member(name(element), type, false, particle.isRepeated(), schema));
    } else if (term.isModelGroup() || term.isModelGroupDecl()) {
      XSModelGroup group = term.isModelGroup() ? term.asModelGroup() : term.asModelGroupDecl().getModelGroup();
      if (group.getCompositor() == XSModelGroup.CHOICE || particle.isRepeated()) {
        throw new ConfigException("Unsupported choice or repeated model group in " + path);
      }
      for (XSParticle child : group) {
        members(child, path, optional, members);
      }
    } else {
      throw new ConfigException("Unsupported XML wildcard in " + path);
    }
  }

  private static void checkSubstitution(XSElementDecl element) {
    if (element.getSubstAffiliation() != null || element.getSubstitutables().size() > 1 || element.isAbstract()) {
      throw unsupported(element, "substitution groups or abstract elements");
    }
  }

  private static XmlType scalar(XSSimpleType type, boolean optional) {
    if (type.getBaseListType() != null || type.getBaseUnionType() != null) {
      throw unsupported(type, "list or union simple types");
    }
    XSSimpleType builtin = type;
    while (!XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(builtin.getTargetNamespace())) {
      builtin = builtin.getSimpleBaseType();
    }
    String scalar = builtin.getName();
    SchemaBuilder builder;
    switch (scalar) {
      case "string", "normalizedString", "token", "language", "Name", "NCName", "NMTOKEN", "ID", "IDREF", "ENTITY", "anyURI":
        builder = SchemaBuilder.string();
        scalar = "string";
        break;
      case "boolean":
        builder = SchemaBuilder.bool().optional();
        break;
      case "byte":
        builder = SchemaBuilder.int8().optional();
        break;
      case "short", "unsignedByte":
        builder = SchemaBuilder.int16().optional();
        break;
      case "int", "unsignedShort":
        builder = SchemaBuilder.int32().optional();
        break;
      case "long", "unsignedInt", "unsignedLong", "integer", "positiveInteger",
           "nonNegativeInteger", "nonPositiveInteger", "negativeInteger":
        builder = SchemaBuilder.int64().optional();
        break;
      case "float":
        builder = SchemaBuilder.float32().optional();
        break;
      case "double":
        builder = SchemaBuilder.float64().optional();
        break;
      case "decimal":
        XSFacet scale = type.getFacet(XSFacet.FACET_FRACTIONDIGITS);
        if (scale == null) {
          throw unsupported(type, "decimal without fractionDigits (Connect Decimal requires a fixed scale)");
        }
        builder = Decimal.builder(Integer.parseInt(scale.getValue().value));
        break;
      case "date":
        builder = org.apache.kafka.connect.data.Date.builder();
        break;
      case "time":
        builder = org.apache.kafka.connect.data.Time.builder();
        break;
      case "dateTime":
        builder = org.apache.kafka.connect.data.Timestamp.builder();
        break;
      case "base64Binary", "hexBinary":
        builder = SchemaBuilder.bytes();
        break;
      default:
        throw unsupported(type, "scalar " + scalar);
    }
    if (optional) {
      builder.optional();
    }
    XSFacet whitespace = type.getFacet(XSFacet.FACET_WHITESPACE);
    return new XmlType(builder.build(), scalar, whitespace == null ? "preserve" : whitespace.getValue().value, List.of());
  }

  private static QName name(XSDeclaration declaration) {
    return new QName(declaration.getTargetNamespace(), declaration.getName() == null ? "anonymous" : declaration.getName());
  }

  private static String className(String name) {
    StringBuilder result = new StringBuilder();
    boolean uppercase = true;
    for (char character : name.toCharArray()) {
      if (character == '_' || character == '-' || character == '.') {
        uppercase = true;
      } else {
        result.append(uppercase ? Character.toUpperCase(character) : character);
        uppercase = false;
      }
    }
    return result.toString();
  }

  private static ConfigException unsupported(XSDeclaration declaration, String feature) {
    return new ConfigException("Unsupported " + feature + " for " + name(declaration)
        + " at " + (declaration.getLocator() == null ? "unknown location" :
        declaration.getLocator().getSystemId() + ":" + declaration.getLocator().getLineNumber()));
  }
}
