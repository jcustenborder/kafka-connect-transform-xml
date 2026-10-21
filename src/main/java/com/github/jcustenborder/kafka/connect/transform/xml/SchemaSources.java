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

import com.sun.xml.xsom.parser.SchemaDocument;
import org.apache.kafka.common.config.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Configuration-time schema cache shared by XSOM and JAXP. */
final class SchemaSources implements EntityResolver, LSResourceResolver {
  private static final Logger log = LoggerFactory.getLogger(SchemaSources.class);
  private final Map<String, byte[]> documents = new HashMap<>();
  private final Map<String, String> namespaces = new HashMap<>();
  private final Map<String, Set<String>> imports = new HashMap<>();
  private final Map<String, String> umbrellas = new LinkedHashMap<>();
  private final DocumentBuilder metadata;
  private final DOMImplementationLS dom;

  SchemaSources(List<URL> roots) throws ParserConfigurationException, IOException, SAXException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
    factory.setNamespaceAware(true);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    metadata = factory.newDocumentBuilder();
    metadata.setErrorHandler(new ConnectErrorListener(log));
    dom = (DOMImplementationLS) metadata.getDOMImplementation().getFeature("LS", "3.0");
    Map<String, List<String>> groups = new LinkedHashMap<>();
    for (URL root : roots) {
      String id = source(root).getSystemId();
      groups.computeIfAbsent(namespaces.get(id), ignored -> new ArrayList<>()).add(id);
    }
    compose(groups);
  }

  InputSource source(URL url) throws IOException, SAXException {
    String systemId = url.toExternalForm();
    byte[] bytes = documents.get(systemId);
    if (bytes == null) {
      if (!Set.of("file", "http", "https").contains(url.getProtocol())) {
        throw new ConfigException("Unsupported schema URL protocol: " + url);
      }
      log.debug("Loading XSD {}", url);
      URLConnection connection = url.openConnection();
      connection.setConnectTimeout(30000);
      connection.setReadTimeout(30000);
      try (InputStream input = connection.getInputStream()) {
        bytes = input.readAllBytes();
      }
      Element schema = metadata.parse(input(systemId, bytes)).getDocumentElement();
      if (!XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(schema.getNamespaceURI())
          || !"schema".equals(schema.getLocalName())) {
        throw new ConfigException("Expected an XSD schema document at " + systemId);
      }
      namespaces.put(systemId, schema.getAttribute("targetNamespace"));
      Set<String> importedNamespaces = new HashSet<>();
      for (Node child = schema.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child instanceof Element && XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(child.getNamespaceURI())
            && "import".equals(child.getLocalName())) {
          importedNamespaces.add(((Element) child).getAttribute("namespace"));
        }
      }
      imports.put(systemId, importedNamespaces);
      documents.put(systemId, bytes);
    }
    return input(systemId, bytes);
  }

  private static InputSource input(String systemId, byte[] bytes) {
    InputSource input = new InputSource(new ByteArrayInputStream(bytes));
    input.setSystemId(systemId);
    return input;
  }

  @Override
  public InputSource resolveEntity(String publicId, String systemId) throws IOException, SAXException {
    if (systemId == null || systemId.isEmpty()) {
      String id = umbrellas.get(publicId == null ? "" : publicId);
      if (id == null) {
        throw new ConfigException("Namespace-only imports require a configured XSD for " + publicId);
      }
      return input(id, documents.get(id));
    }
    if (documents.containsKey(systemId)) {
      return input(systemId, documents.get(systemId));
    }
    return source(new URL(systemId));
  }

  Source[] validationSources() {
    return umbrellas.values().stream().map(id -> new StreamSource(new ByteArrayInputStream(documents.get(id)), id))
        .toArray(Source[]::new);
  }

  void complete(Set<SchemaDocument> graph) {
    Map<String, List<String>> groups = new LinkedHashMap<>();
    Set<String> virtualDocuments = new HashSet<>(umbrellas.values());
    for (SchemaDocument document : graph) {
      String id = document.getSystemId();
      if (documents.containsKey(id) && !virtualDocuments.contains(id)) {
        groups.computeIfAbsent(document.getTargetNamespace(), ignored -> new ArrayList<>()).add(id);
      }
    }
    compose(groups);
  }

  private void compose(Map<String, List<String>> groups) {
    for (Map.Entry<String, List<String>> group : groups.entrySet()) {
      String id = umbrellas.computeIfAbsent(group.getKey(), ignored -> "urn:kafka-connect-xml:namespace:" + umbrellas.size());
      documents.put(id, umbrella(group.getKey(), group.getValue()));
      namespaces.put(id, group.getKey());
    }
  }

  @Override
  public LSInput resolveResource(String type, String namespace, String publicId, String systemId, String baseUri) {
    try {
      String namespaceKey = namespace == null ? "" : namespace;
      String id;
      if (umbrellas.containsKey(namespaceKey) && imports.getOrDefault(baseUri, Set.of()).contains(namespaceKey)) {
        id = umbrellas.get(namespaceKey);
      } else {
        if (systemId == null || baseUri == null) {
          throw new ConfigException("Schema import has no configured XSD or schemaLocation: " + namespace);
        }
        id = URI.create(baseUri).resolve(systemId).normalize().toURL().toExternalForm();
      }
      byte[] bytes = documents.get(id);
      if (bytes == null) {
        throw new ConfigException("Schema was not loaded from the configured XSD graph: " + id);
      }
      LSInput input = dom.createLSInput();
      input.setSystemId(id);
      input.setPublicId(publicId);
      input.setBaseURI(baseUri);
      input.setByteStream(new ByteArrayInputStream(bytes));
      return input;
    } catch (IOException e) {
      ConfigException error = new ConfigException("Invalid schema location: " + systemId);
      error.initCause(e);
      throw error;
    }
  }

  /** A single include graph resolves cross-file references independently of input order. */
  private static byte[] umbrella(String namespace, List<String> locations) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      XMLStreamWriter xml = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(bytes, "UTF-8");
      xml.writeStartDocument("UTF-8", "1.0");
      xml.writeStartElement("xs", "schema", XMLConstants.W3C_XML_SCHEMA_NS_URI);
      xml.writeNamespace("xs", XMLConstants.W3C_XML_SCHEMA_NS_URI);
      if (!namespace.isEmpty()) {
        xml.writeAttribute("targetNamespace", namespace);
      }
      for (String location : locations) {
        xml.writeEmptyElement("xs", "include", XMLConstants.W3C_XML_SCHEMA_NS_URI);
        xml.writeAttribute("schemaLocation", location);
      }
      xml.writeEndElement();
      xml.writeEndDocument();
      xml.close();
      return bytes.toByteArray();
    } catch (XMLStreamException e) {
      throw new IllegalStateException("Cannot compose configured schema documents", e);
    }
  }
}
