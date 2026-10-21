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

import org.apache.kafka.connect.data.ConnectSchema;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PublicNamespaceTest {
  private static final String FIXTURES = "public/namespaces/";
  private static final String MODEL = "com.github.jcustenborder.kafka.connect.transform.xml.publicmodel";

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void resolvesRelativeImportAndIncludeFromRootUrl(boolean bytes) throws IOException {
    try (FromXml.Value<SinkRecord> transform = configured()) {
      assertOutput(transform.apply(record(xml(), bytes)));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void namespacePrefixesDoNotChangeOutput(boolean bytes) throws IOException {
    String equivalent = xml().replace("xmlns:root=", "xmlns:message=")
        .replace("root:", "message:")
        .replace("xmlns:data=", "xmlns:payload=").replace("data:", "payload:");
    try (FromXml.Value<SinkRecord> transform = configured()) {
      SinkRecord original = transform.apply(record(xml(), bytes));
      SinkRecord renamed = transform.apply(record(equivalent, bytes));
      assertOutput(original);
      assertOutput(renamed);
      assertEquals(original.valueSchema(), renamed.valueSchema());
      assertEquals(original.value(), renamed.value());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void rejectsRootInWrongNamespace(boolean bytes) throws IOException {
    String invalid = xml().replace("http://soapinterop.org/xsd\"", "urn:wrong:root\"");
    try (FromXml.Value<SinkRecord> transform = configured()) {
      assertThrows(DataException.class, () -> transform.apply(record(invalid, bytes)));
    }
  }

  private static FromXml.Value<SinkRecord> configured() {
    FromXml.Value<SinkRecord> transform = new FromXml.Value<>();
    transform.configure(Map.of(
        FromXmlConfig.SCHEMA_PATH_CONFIG, List.of(Objects.requireNonNull(
            PublicNamespaceTest.class.getResource(FIXTURES + "importBase.xsd")).toExternalForm()),
        FromXmlConfig.PACKAGE_CONFIG, MODEL));
    return transform;
  }

  private static String xml() throws IOException {
    try (InputStream input = Objects.requireNonNull(
        PublicNamespaceTest.class.getResourceAsStream(FIXTURES + "echo.xml"))) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static SinkRecord record(String xml, boolean bytes) {
    return new SinkRecord("public-namespaces", 0, null, null,
        bytes ? Schema.BYTES_SCHEMA : Schema.STRING_SCHEMA,
        bytes ? xml.getBytes(StandardCharsets.UTF_8) : xml, 0L);
  }

  private static void assertOutput(SinkRecord record) {
    Struct value = assertInstanceOf(Struct.class, record.value());
    ConnectSchema.validateValue(record.valueSchema(), value);
    assertEquals(Schema.Type.STRUCT, record.valueSchema().type());
    assertEquals(MODEL + ".SOAPStruct", record.valueSchema().name());
    assertEquals(record.valueSchema(), value.schema());
    // Local XML names are the existing Connect wire names, without prefix/namespace decoration.
    assertEquals(List.of("varString", "sourceId"), value.schema().fields().stream()
        .map(field -> field.name()).collect(Collectors.toList()));
    assertEquals(Schema.STRING_SCHEMA, value.schema().field("varString").schema());
    assertEquals(Schema.STRING_SCHEMA, value.schema().field("sourceId").schema());
    assertEquals("Hello from the imported type", value.getString("varString"));
    assertEquals("sample-001", value.getString("sourceId"));
  }
}
