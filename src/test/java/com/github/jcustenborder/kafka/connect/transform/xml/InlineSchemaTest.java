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

import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.data.ConnectSchema;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InlineSchemaTest {
  private static final String XSD = """
      <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <xs:element name="message">
          <xs:complexType><xs:sequence>
            <xs:element name="text" type="xs:string"/>
            <xs:element name="count"><xs:simpleType><xs:restriction base="xs:int">
              <xs:minInclusive value="1"/>
            </xs:restriction></xs:simpleType></xs:element>
          </xs:sequence></xs:complexType>
        </xs:element>
      </xs:schema>
      """;
  private static final String XML = "<message><text>café</text><count>7</count></message>";

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void convertsInlineSchemaOnEitherRecordSide(boolean key) {
    FromXml<SinkRecord> transform = key ? new FromXml.Key<>() : new FromXml.Value<>();
    try {
      transform.configure(Map.of("schema.content", XSD, "schema.path", "", "package", "inline.model"));
      SinkRecord input = new SinkRecord("messages", 0, Schema.STRING_SCHEMA, key ? XML : "keep-key",
          Schema.STRING_SCHEMA, key ? "keep-value" : XML, 3L);
      SinkRecord result = transform.apply(input);
      Struct value = (Struct) (key ? result.key() : result.value());
      assertEquals("inline.model.Message", value.schema().name());
      assertEquals("café", value.getString("text"));
      assertEquals(7, value.getInt32("count"));
      ConnectSchema.validateValue(value.schema(), value);
      assertSame(key ? input.value() : input.key(), key ? result.value() : result.key());
    } finally {
      transform.close();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"UTF-8", "UTF-16", "ISO-8859-1"})
  public void configStringPreservesUnicodeRegardlessOfXmlEncodingDeclaration(String encoding) {
    String schema = "<?xml version='1.0' encoding='" + encoding + "'?>"
        + XSD.replace("name=\"text\"", "name=\"café\"");
    FromXml<SinkRecord> transform = new FromXml.Value<>();
    try {
      transform.configure(Map.of("schema.content", schema));
      Struct value = (Struct) transform.apply(record(XML.replace("text", "café"))).value();
      assertEquals("café", value.getString("café"));
    } finally {
      transform.close();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"<xs:schema", "<wrong/>",
      "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'><xs:element name='bad' type='xs:missing'/></xs:schema>"})
  public void rejectsMalformedOrInvalidInlineSchema(String schema) {
    ConfigException error = assertThrows(ConfigException.class,
        () -> new FromXml.Value<>().configure(Map.of("schema.content", schema)));
    assertTrue(error.getMessage().contains("XSD"), error.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"<message><text>ok</text><count>bad</count></message>",
      "<message><text>ok</text><count>0</count></message>",
      "<message><text>ok</text></message>", "<wrong/>", "<message>",
      "<!DOCTYPE message [<!ENTITY external SYSTEM 'file:///nonexistent'>]><message><text>&external;</text><count>7</count></message>"})
  public void inlineSchemaStillValidatesEveryXmlDocument(String xml) {
    FromXml<SinkRecord> transform = new FromXml.Value<>();
    try {
      transform.configure(Map.of("schema.content", XSD));
      assertThrows(DataException.class, () -> transform.apply(record(xml)));
    } finally {
      transform.close();
    }
  }

  @Test
  public void rejectsDoctypeInInlineSchema() {
    String schema = "<!DOCTYPE schema [<!ENTITY external SYSTEM 'file:///nonexistent'>]>" + XSD;
    ConfigException error = assertThrows(ConfigException.class,
        () -> new FromXml.Value<>().configure(Map.of("schema.content", schema)));
    assertTrue(error.getMessage().contains("DOCTYPE"), error.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"include", "import"})
  public void resolvesAbsoluteReferencesAndTheirRelativeDependencies(String directive, @TempDir Path directory)
      throws Exception {
    Files.writeString(directory.resolve("types.xsd"), XSD, StandardCharsets.UTF_8);
    Files.writeString(directory.resolve("root.xsd"), """
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <xs:include schemaLocation="types.xsd"/>
        </xs:schema>
        """, StandardCharsets.UTF_8);
    String schema = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'"
        + (directive.equals("import") ? " targetNamespace='urn:inline:root'" : "") + "><xs:" + directive
        + " schemaLocation='" + directory.resolve("root.xsd").toUri() + "'/></xs:schema>";
    FromXml<SinkRecord> transform = new FromXml.Value<>();
    try {
      transform.configure(Map.of("schema.content", schema));
      Struct result = (Struct) transform.apply(record(XML)).value();
      assertEquals("café", result.getString("text"));
      assertEquals(7, result.getInt32("count"));
    } finally {
      transform.close();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"include", "import", "redefine"})
  public void rejectsRelativeReferencesWithoutABaseUrl(String directive) {
    String schema = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'><xs:" + directive
        + " schemaLocation='relative.xsd'/></xs:schema>";
    ConfigException error = assertThrows(ConfigException.class,
        () -> new FromXml.Value<>().configure(Map.of("schema.content", schema)));
    assertTrue(error.getMessage().contains("schema.content"), error.getMessage());
    assertTrue(error.getMessage().contains("relative.xsd"), error.getMessage());
  }

  private static SinkRecord record(String xml) {
    return new SinkRecord("messages", 0, null, null, Schema.STRING_SCHEMA, xml, 0L);
  }
}
