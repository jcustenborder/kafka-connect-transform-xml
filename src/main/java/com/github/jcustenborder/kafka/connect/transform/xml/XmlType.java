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

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Connect type metadata and conversion of an already XSD-validated element. */
final class XmlType {
  private static final Logger log = LoggerFactory.getLogger(XmlType.class);
  final Schema schema;
  private final String scalar;
  private final String whitespace;
  private final List<Member> members;

  XmlType(Schema schema, String scalar, String whitespace, List<Member> members) {
    this.schema = schema;
    this.scalar = scalar;
    this.whitespace = whitespace;
    this.members = List.copyOf(members);
  }

  Object read(Element element) {
    if (element.hasAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type")) {
      throw new DataException("Dynamic xsi:type has no fixed Connect schema mapping: " + element.getTagName());
    }
    String nil = element.getAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "nil");
    if ("true".equals(nil) || "1".equals(nil)) {
      return null;
    }
    if (scalar != null) {
      return convert(element.getTextContent(), element.getTagName());
    }
    Map<QName, List<Element>> children = new HashMap<>();
    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element) {
        Element value = (Element) child;
        children.computeIfAbsent(name(value), ignored -> new ArrayList<>()).add(value);
      }
    }
    Struct result = new Struct(schema);
    for (Member member : members) {
      Object value = null;
      if (member.attribute()) {
        String namespace = member.name().getNamespaceURI();
        Attr attribute = element.getAttributeNodeNS(namespace.isEmpty() ? null : namespace, member.name().getLocalPart());
        if (attribute != null) {
          value = member.type().convert(attribute.getValue(), member.name().toString());
        }
      } else {
        List<Element> matches = children.get(member.name());
        if (matches != null) {
          if (member.repeated()) {
            List<Object> items = new ArrayList<>(matches.size());
            for (Element match : matches) {
              items.add(member.type().read(match));
            }
            value = items;
          } else {
            if (matches.size() != 1) {
              throw new DataException("Multiple XML elements for non-array field " + member.name());
            }
            value = member.type().read(matches.get(0));
          }
        }
      }
      result.put(member.name().getLocalPart(), value);
    }
    return result;
  }

  static QName name(Element element) {
    return new QName(element.getNamespaceURI() == null ? "" : element.getNamespaceURI(), element.getLocalName());
  }

  private Object convert(String text, String field) {
    String value = text;
    if (!"preserve".equals(whitespace)) {
      value = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
      if ("collapse".equals(whitespace)) {
        value = value.trim().replaceAll(" +", " ");
      }
    }
    log.trace("Mapping XML field {} to Connect {}", field, schema.type());
    try {
      return switch (scalar) {
        case "string" -> value;
        case "boolean" -> switch (value) {
          case "true", "1" -> true;
          case "false", "0" -> false;
          default -> throw new IllegalArgumentException("Invalid XML boolean");
        };
        case "byte" -> Byte.valueOf(value);
        case "short", "unsignedByte" -> Short.valueOf(value);
        case "int", "unsignedShort" -> Integer.valueOf(value);
        case "long", "unsignedInt", "unsignedLong", "integer", "positiveInteger",
             "nonNegativeInteger", "nonPositiveInteger", "negativeInteger" -> Long.valueOf(value);
        case "float" -> Float.valueOf(floatingPoint(value));
        case "double" -> Double.valueOf(floatingPoint(value));
        case "decimal" -> new BigDecimal(value).setScale(Integer.parseInt(schema.parameters().get("scale")));
        case "base64Binary" -> Base64.getMimeDecoder().decode(value);
        case "hexBinary" -> HexFormat.of().parseHex(value);
        case "date", "time", "dateTime" -> date(value);
        default -> throw new IllegalStateException("Unsupported XML scalar " + scalar);
      };
    } catch (IllegalArgumentException | ArithmeticException | DateTimeException e) {
      throw new DataException("Cannot map XML field " + field + " to Connect " + schema.type(), e);
    }
  }

  private static String floatingPoint(String value) {
    return switch (value) {
      case "INF" -> "Infinity";
      case "-INF" -> "-Infinity";
      default -> value;
    };
  }

  private Date date(String value) {
    XMLGregorianCalendar calendar = DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar(value);
    if ("date".equals(scalar)) {
      return org.apache.kafka.connect.data.Date.toLogical(schema, Math.toIntExact(day(calendar).toEpochDay()));
    }
    int millis = calendar.getFractionalSecond() == null ? 0
        : calendar.getFractionalSecond().movePointRight(3).intValueExact();
    LocalTime time = LocalTime.of(calendar.getHour(), calendar.getMinute(), calendar.getSecond(), millis * 1000000);
    ZoneOffset offset = calendar.getTimezone() == DatatypeConstants.FIELD_UNDEFINED ? ZoneOffset.UTC
        : ZoneOffset.ofTotalSeconds(calendar.getTimezone() * 60);
    if ("time".equals(scalar)) {
      long epoch = time.atDate(LocalDate.ofEpochDay(0)).atOffset(offset).toInstant().toEpochMilli();
      return new Date(Math.floorMod(epoch, 86400000L));
    }
    return Date.from(OffsetDateTime.of(day(calendar), time, offset).toInstant());
  }

  private static LocalDate day(XMLGregorianCalendar calendar) {
    int year = calendar.getEonAndYear().intValueExact();
    // XML Schema 1.0 has no year zero; ISO dates number 1 BCE as year zero.
    return LocalDate.of(year < 0 ? year + 1 : year, calendar.getMonth(), calendar.getDay());
  }

  record Member(QName name, XmlType type, boolean attribute, boolean repeated, Schema schema) {
  }
}
