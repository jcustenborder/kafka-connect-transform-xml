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
import org.apache.kafka.connect.data.Decimal;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DirectMappingTest {
  @TempDir
  Path directory;
  private FromXml.Value<SinkRecord> transform;

  @AfterEach
  public void close() {
    if (transform != null) {
      transform.close();
    }
  }

  private void configure(String schema, Map<String, Object> options) throws IOException {
    Path file = Files.writeString(directory.resolve("schema.xsd"), schema);
    Map<String, Object> settings = new java.util.HashMap<>(options);
    settings.put(FromXmlConfig.SCHEMA_PATH_CONFIG, file.toUri().toString());
    FromXml.Value<SinkRecord> candidate = new FromXml.Value<>();
    candidate.configure(settings);
    transform = candidate;
  }

  private void fields(String fields, String definitions) throws IOException {
    configure(schema("<xs:element name='record' type='Record'/>"
        + "<xs:complexType name='Record'><xs:sequence>" + fields
        + "</xs:sequence></xs:complexType>" + definitions), Map.of());
  }

  private static String schema(String body) {
    return "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>" + body + "</xs:schema>";
  }

  private Struct apply(String xml, boolean bytes) {
    Object value = bytes ? xml.getBytes(StandardCharsets.UTF_8) : xml;
    SinkRecord result = transform.apply(new SinkRecord("test", 0, null, null,
        bytes ? Schema.BYTES_SCHEMA : Schema.STRING_SCHEMA, value, 0));
    Struct struct = assertInstanceOf(Struct.class, result.value());
    ConnectSchema.validateValue(result.valueSchema(), struct);
    return struct;
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void mapsLogicalTypesToConnectValues(boolean bytes) throws IOException {
    fields("<xs:element name='day' type='xs:date'/><xs:element name='clock' type='xs:time'/>"
        + "<xs:element name='stamp' type='xs:dateTime'/><xs:element name='amount' type='Money'/>",
        "<xs:simpleType name='Money'><xs:restriction base='xs:decimal'>"
            + "<xs:fractionDigits value='2'/></xs:restriction></xs:simpleType>");
    Struct result = apply("<record><day>2000-10-01</day><clock>12:34:56.789Z</clock>"
        + "<stamp>2020-01-02T03:04:05.123+02:00</stamp><amount>12.3</amount></record>", bytes);
    assertEquals(org.apache.kafka.connect.data.Date.SCHEMA, result.schema().field("day").schema());
    assertEquals(Date.from(Instant.parse("2000-10-01T00:00:00Z")), result.get("day"));
    assertEquals(org.apache.kafka.connect.data.Time.LOGICAL_NAME,
        result.schema().field("clock").schema().name());
    assertEquals(new Date(45296789), result.get("clock"));
    assertEquals(org.apache.kafka.connect.data.Timestamp.LOGICAL_NAME,
        result.schema().field("stamp").schema().name());
    assertEquals(Date.from(Instant.parse("2020-01-02T01:04:05.123Z")), result.get("stamp"));
    assertEquals(Decimal.schema(2), result.schema().field("amount").schema());
    assertEquals(new BigDecimal("12.30"), result.get("amount"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void mapsBinaryAndUnsignedValuesWithoutTruncation(boolean bytes) throws IOException {
    fields("<xs:element name='base64' type='xs:base64Binary'/><xs:element name='hex' type='xs:hexBinary'/>"
        + "<xs:element name='unsigned' type='xs:unsignedInt'/><xs:element name='large' type='xs:integer'/>", "");
    Struct result = apply("<record><base64>AAH/</base64><hex>0001ff</hex>"
        + "<unsigned>4294967295</unsigned><large>9223372036854775807</large></record>", bytes);
    assertEquals(Schema.Type.BYTES, result.schema().field("base64").schema().type());
    assertEquals(Schema.Type.BYTES, result.schema().field("hex").schema().type());
    assertArrayEquals(new byte[]{0, 1, -1}, result.getBytes("base64"));
    assertArrayEquals(new byte[]{0, 1, -1}, result.getBytes("hex"));
    assertEquals(Schema.Type.INT64, result.schema().field("unsigned").schema().type());
    assertEquals(4294967295L, result.getInt64("unsigned").longValue());
    assertEquals(Schema.Type.INT64, result.schema().field("large").schema().type());
    assertEquals(Long.valueOf(Long.MAX_VALUE), result.getInt64("large"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void rejectsUnboundedIntegersOutsideConnectInt64(boolean bytes) throws IOException {
    fields("<xs:element name='value' type='xs:integer'/>", "");
    assertThrows(DataException.class, () -> apply(
        "<record><value>9223372036854775808</value></record>", bytes));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void mapsReferencesInheritanceDefaultsAndRepeatedScalars(boolean bytes) throws IOException {
    configure(schema("<xs:element name='record' type='Derived'/><xs:element name='label' type='xs:string'/>"
        + "<xs:complexType name='Base'><xs:sequence><xs:element name='code' type='xs:token'/>"
        + "</xs:sequence><xs:attribute name='status' type='xs:string' default='new'/></xs:complexType>"
        + "<xs:complexType name='Derived'><xs:complexContent><xs:extension base='Base'><xs:sequence>"
        + "<xs:element ref='label' maxOccurs='unbounded'/><xs:element name='child'><xs:complexType>"
        + "<xs:attribute name='id' type='xs:int' use='required'/></xs:complexType></xs:element>"
        + "</xs:sequence></xs:extension></xs:complexContent></xs:complexType>"), Map.of());
    Struct result = apply("<record><code>  a   b  </code><label>second</label><label>first</label>"
        + "<child id='7'/></record>", bytes);
    assertEquals("a b", result.getString("code"));
    assertEquals("new", result.getString("status"));
    assertEquals(Schema.Type.ARRAY, result.schema().field("label").schema().type());
    assertEquals(Schema.Type.STRING, result.schema().field("label").schema().valueSchema().type());
    assertEquals(List.of("second", "first"), result.getArray("label"));
    assertEquals(Integer.valueOf(7), result.getStruct("child").getInt32("id"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void loadsMultipleConfiguredSchemasForOneNamespace(boolean bytes) throws IOException {
    Path first = Files.writeString(directory.resolve("first.xsd"), schema(
        "<xs:element name='record'><xs:complexType><xs:sequence>"
            + "<xs:element name='value' type='xs:string'/></xs:sequence></xs:complexType></xs:element>"));
    Path second = Files.writeString(directory.resolve("second.xsd"), schema(
        "<xs:element name='other'><xs:complexType><xs:sequence>"
            + "<xs:element name='number' type='xs:int'/></xs:sequence></xs:complexType></xs:element>"));
    FromXml.Value<SinkRecord> candidate = new FromXml.Value<>();
    candidate.configure(Map.of(FromXmlConfig.SCHEMA_PATH_CONFIG,
        List.of(first.toUri().toString(), second.toUri().toString())));
    transform = candidate;
    assertEquals("one", apply("<record><value>one</value></record>", bytes).getString("value"));
    assertEquals(Integer.valueOf(2), apply("<other><number>2</number></other>", bytes).getInt32("number"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void resolvesCrossDocumentReferencesInEitherConfigurationOrder(boolean bytes) throws IOException {
    Path first = Files.writeString(directory.resolve("first.xsd"), schema(
        "<xs:element name='record'><xs:complexType><xs:sequence>"
            + "<xs:element name='value' type='B'/></xs:sequence></xs:complexType></xs:element>"
            + "<xs:simpleType name='A'><xs:restriction base='xs:string'/></xs:simpleType>"));
    Path second = Files.writeString(directory.resolve("second.xsd"), schema(
        "<xs:simpleType name='B'><xs:restriction base='A'><xs:maxLength value='5'/>"
            + "</xs:restriction></xs:simpleType>"));
    for (List<Path> order : List.of(List.of(first, second), List.of(second, first))) {
      FromXml.Value<SinkRecord> candidate = new FromXml.Value<>();
      candidate.configure(Map.of(FromXmlConfig.SCHEMA_PATH_CONFIG,
          List.of(order.get(0).toUri().toString(), order.get(1).toUri().toString())));
      transform = candidate;
      Struct result = apply("<record><value>text</value></record>", bytes);
      assertEquals(Schema.STRING_SCHEMA, result.schema().field("value").schema());
      assertEquals("text", result.getString("value"));
      transform.close();
      transform = null;
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void resolvesNamespaceOnlyImportsFromConfiguredSchemas(boolean bytes) throws IOException {
    Path first = Files.writeString(directory.resolve("a.xsd"),
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:b='urn:b' targetNamespace='urn:a'>"
            + "<xs:import namespace='urn:b'/><xs:element name='record' type='b:Payload'/></xs:schema>");
    Path second = Files.writeString(directory.resolve("b.xsd"),
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:b'>"
            + "<xs:complexType name='Payload'><xs:attribute name='id' type='xs:string' use='required'/>"
            + "</xs:complexType></xs:schema>");
    for (List<Path> order : List.of(List.of(first, second), List.of(second, first))) {
      FromXml.Value<SinkRecord> candidate = new FromXml.Value<>();
      candidate.configure(Map.of(FromXmlConfig.SCHEMA_PATH_CONFIG,
          List.of(order.get(0).toUri().toString(), order.get(1).toUri().toString())));
      transform = candidate;
      Struct result = apply("<record xmlns='urn:a' id='imported'/>", bytes);
      assertEquals(Schema.STRING_SCHEMA, result.schema().field("id").schema());
      assertEquals("imported", result.getString("id"));
      transform.close();
      transform = null;
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void combinesExplicitImportsWithConfiguredNamespaceSiblings(boolean bytes) throws IOException {
    Path imported = Files.writeString(directory.resolve("c.xsd"),
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:b'>"
            + "<xs:complexType name='Payload'><xs:attribute name='id' type='xs:string' use='required'/>"
            + "</xs:complexType></xs:schema>");
    Path first = Files.writeString(directory.resolve("a.xsd"),
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:b='urn:b' targetNamespace='urn:a'>"
            + "<xs:import namespace='urn:b' schemaLocation='" + imported.getFileName() + "'/>"
            + "<xs:element name='record' type='b:Payload'/></xs:schema>");
    Path sibling = Files.writeString(directory.resolve("b.xsd"),
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:b'>"
            + "<xs:complexType name='Other'><xs:attribute name='unused' type='xs:string'/>"
            + "</xs:complexType></xs:schema>");
    for (List<Path> order : List.of(List.of(first), List.of(first, sibling), List.of(sibling, first))) {
      FromXml.Value<SinkRecord> candidate = new FromXml.Value<>();
      candidate.configure(Map.of(FromXmlConfig.SCHEMA_PATH_CONFIG,
          order.stream().map(path -> path.toUri().toString()).toList()));
      transform = candidate;
      Struct result = apply("<record xmlns='urn:a' id='payload'/>", bytes);
      assertEquals(Schema.STRING_SCHEMA, result.schema().field("id").schema());
      assertEquals("payload", result.getString("id"));
      transform.close();
      transform = null;
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void adoptsNamespacesThroughNestedChameleonIncludes(boolean bytes) throws IOException {
    Files.writeString(directory.resolve("scalar.xsd"), schema(
        "<xs:simpleType name='Code'><xs:restriction base='xs:string'>"
            + "<xs:pattern value='[A-Z]{3}'/></xs:restriction></xs:simpleType>"));
    Files.writeString(directory.resolve("types.xsd"), schema(
        "<xs:include schemaLocation='scalar.xsd'/><xs:complexType name='Payload'><xs:sequence>"
            + "<xs:element name='value' type='Code'/></xs:sequence></xs:complexType>"));
    configure("<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:a='urn:a' targetNamespace='urn:a'>"
        + "<xs:include schemaLocation='types.xsd'/><xs:element name='record' type='a:Payload'/></xs:schema>", Map.of());
    Struct result = apply("<a:record xmlns:a='urn:a'><value>ABC</value></a:record>", bytes);
    assertEquals(Schema.STRING_SCHEMA, result.schema().field("value").schema());
    assertEquals("ABC", result.getString("value"));
    assertThrows(DataException.class, () -> apply(
        "<a:record xmlns:a='urn:a'><value>123</value></a:record>", bytes));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void enforcesConnectLogicalDateStorageBoundaries(boolean bytes) throws IOException {
    fields("<xs:element name='value' type='xs:date'/>", "");
    Struct maximum = apply("<record><value>5881580-07-11</value></record>", bytes);
    assertEquals(Integer.MAX_VALUE, org.apache.kafka.connect.data.Date.fromLogical(
        maximum.schema().field("value").schema(), (Date) maximum.get("value")));
    Struct minimum = apply("<record><value>-5877642-06-23</value></record>", bytes);
    assertEquals(Integer.MIN_VALUE, org.apache.kafka.connect.data.Date.fromLogical(
        minimum.schema().field("value").schema(), (Date) minimum.get("value")));
    assertThrows(DataException.class, () -> apply("<record><value>5881580-07-12</value></record>", bytes));
    assertThrows(DataException.class, () -> apply("<record><value>-5877642-06-22</value></record>", bytes));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void mapsXmlBceYearsToIsoDateAndTimestampValues(boolean bytes) throws IOException {
    fields("<xs:element name='day' type='xs:date'/><xs:element name='stamp' type='xs:dateTime'/>", "");
    Struct result = apply("<record><day>-0001-01-01</day><stamp>-0001-01-01T00:00:00Z</stamp></record>", bytes);
    Date expected = Date.from(Instant.parse("0000-01-01T00:00:00Z"));
    assertEquals(expected, result.get("day"));
    assertEquals(expected, result.get("stamp"));
    assertEquals(-719528, org.apache.kafka.connect.data.Date.fromLogical(
        result.schema().field("day").schema(), (Date) result.get("day")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void rejectsDtdsAndExternalEntities(boolean bytes) throws IOException {
    fields("<xs:element name='value' type='xs:string'/>", "");
    Path secret = Files.writeString(directory.resolve("secret.txt"), "must-not-be-read");
    String xml = "<!DOCTYPE record [<!ENTITY external SYSTEM '" + secret.toUri()
        + "'>]><record><value>&external;</value></record>";
    assertThrows(DataException.class, () -> apply(xml, bytes));
    assertThrows(DataException.class, () -> apply(
        "<!DOCTYPE record [<!ENTITY internal 'text'>]><record><value>&internal;</value></record>", bytes));
    assertEquals("valid", apply("<record><value>valid</value></record>", bytes).getString("value"));
  }

  @Test
  public void rejectsDtdInConfiguredSchema() throws IOException {
    assertThrows(ConfigException.class, () -> configure("<!DOCTYPE xs:schema [<!ENTITY name 'value'>]>"
        + schema("<xs:element name='record'><xs:complexType><xs:sequence>"
            + "<xs:element name='&name;' type='xs:string'/></xs:sequence></xs:complexType></xs:element>"),
        Map.of()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void legacyStrictCheckCannotDisableDocumentValidation(boolean bytes) throws IOException {
    configure(schema("<xs:element name='record'><xs:complexType><xs:sequence>"
        + "<xs:element name='number' type='xs:int'/></xs:sequence>"
        + "<xs:attribute name='id' type='xs:string' use='required'/></xs:complexType></xs:element>"),
        Map.of(FromXmlConfig.XJC_OPTIONS_STRICT_CHECK_CONFIG, false));
    assertThrows(DataException.class, () -> apply("<record id='1'/>", bytes));
    assertThrows(DataException.class, () -> apply("<record><number>1</number></record>", bytes));
    assertThrows(DataException.class, () -> apply("<record id='1'><number>1</number><extra/></record>", bytes));
    assertEquals(Integer.valueOf(1), apply("<record id='1'><number>1</number></record>", bytes).getInt32("number"));
  }

  @Test
  public void acceptsByteBufferWithoutChangingItsPosition() throws IOException {
    fields("<xs:element name='value' type='xs:string'/>", "");
    byte[] xml = "prefix<record><value>text</value></record>suffix".getBytes(StandardCharsets.UTF_8);
    ByteBuffer input = ByteBuffer.wrap(xml);
    input.position(6);
    input.limit(xml.length - 6);
    SinkRecord result = transform.apply(new SinkRecord("test", 0, null, null,
        Schema.BYTES_SCHEMA, input, 0));
    ConnectSchema.validateValue(result.valueSchema(), result.value());
    assertEquals("text", ((Struct) result.value()).getString("value"));
    assertEquals(6, input.position());
    assertEquals(xml.length - 6, input.limit());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void mapsAndValidatesEnumerationsAsConnectStrings(boolean bytes) throws IOException {
    fields("<xs:element name='level' type='Level'/>",
        "<xs:simpleType name='Level'><xs:restriction base='xs:string'>"
            + "<xs:enumeration value='Silver'/><xs:enumeration value='Gold'/>"
            + "</xs:restriction></xs:simpleType>");
    Struct result = apply("<record><level>Gold</level></record>", bytes);
    assertEquals(Schema.STRING_SCHEMA, result.schema().field("level").schema());
    assertEquals("Gold", result.getString("level"));
    assertThrows(DataException.class, () -> apply("<record><level>Other</level></record>", bytes));
    assertEquals("Silver", apply("<record><level>Silver</level></record>", bytes).getString("level"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void rejectsDynamicTypesRatherThanDiscardingDerivedFields(boolean bytes) throws IOException {
    configure(schema("<xs:element name='record' type='Base'/><xs:complexType name='Base'>"
        + "<xs:sequence><xs:element name='value' type='xs:string'/></xs:sequence></xs:complexType>"
        + "<xs:complexType name='Derived'><xs:complexContent><xs:extension base='Base'>"
        + "<xs:sequence><xs:element name='extra' type='xs:string'/></xs:sequence>"
        + "</xs:extension></xs:complexContent></xs:complexType>"), Map.of());
    assertEquals("base", apply("<record><value>base</value></record>", bytes).getString("value"));
    assertThrows(DataException.class, () -> apply(
        "<record xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:type='Derived'>"
            + "<value>base</value><extra>must-not-be-dropped</extra></record>", bytes));
  }

  @Test
  public void rejectsAbstractRootTypesDuringConfiguration() {
    assertThrows(ConfigException.class, () -> configure(schema(
        "<xs:element name='record' type='Record'/><xs:complexType name='Record' abstract='true'>"
            + "<xs:sequence><xs:element name='value' type='xs:string'/></xs:sequence></xs:complexType>"), Map.of()));
  }

  @Test
  public void rejectsInputWhichDoesNotMatchItsConnectSchema() throws IOException {
    fields("<xs:element name='value' type='xs:string'/>", "");
    String xml = "<record><value>text</value></record>";
    assertThrows(DataException.class, () -> transform.apply(new SinkRecord("test", 0, null, null,
        Schema.STRING_SCHEMA, xml.getBytes(StandardCharsets.UTF_8), 0L)));
    assertThrows(DataException.class, () -> transform.apply(new SinkRecord("test", 0, null, null,
        Schema.BYTES_SCHEMA, xml, 0L)));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "<xs:choice><xs:element name='a' type='xs:string'/><xs:element name='b' type='xs:string'/></xs:choice>",
      "<xs:sequence><xs:any processContents='lax'/></xs:sequence>",
      "<xs:sequence><xs:element name='child' type='Record'/></xs:sequence>",
      "<xs:sequence><xs:element name='amount' type='xs:decimal'/></xs:sequence>",
      "<xs:sequence><xs:element name='value' type='xs:string'/></xs:sequence><xs:attribute name='value' type='xs:string'/>",
      "<xs:anyAttribute processContents='lax'/>",
      "<xs:sequence maxOccurs='unbounded'><xs:element name='a' type='xs:string'/><xs:element name='b' type='xs:string'/></xs:sequence>",
      "<xs:simpleContent><xs:extension base='xs:string'/></xs:simpleContent>",
      "<xs:sequence><xs:element name='value' type='xs:duration'/></xs:sequence>",
      "<xs:sequence><xs:element name='value'><xs:simpleType><xs:union memberTypes='xs:string xs:int'/></xs:simpleType></xs:element></xs:sequence>",
      "<xs:sequence><xs:element name='value'><xs:simpleType><xs:list itemType='xs:int'/></xs:simpleType></xs:element></xs:sequence>"
  })
  public void rejectsUnrepresentableSchemasDuringConfiguration(String content) {
    assertThrows(ConfigException.class, () -> configure(schema(
        "<xs:element name='record' type='Record'/><xs:complexType name='Record'>"
            + content + "</xs:complexType>"), Map.of()));
  }

  @Test
  public void rejectsMixedContentRatherThanDiscardingText() {
    assertThrows(ConfigException.class, () -> configure(schema(
        "<xs:element name='record'><xs:complexType mixed='true'><xs:sequence>"
            + "<xs:element name='value' type='xs:string'/></xs:sequence></xs:complexType></xs:element>"), Map.of()));
  }
}
