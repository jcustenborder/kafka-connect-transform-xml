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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PublicRestrictionTest {
  private FromXml<SinkRecord> transform;

  private void configure() {
    FromXml<SinkRecord> candidate = new FromXml.Value<>();
    candidate.configure(Collections.singletonMap(FromXmlConfig.SCHEMA_PATH_CONFIG,
        resource("restrictions.xsd").toExternalForm()));
    this.transform = candidate;
  }

  @AfterEach
  public void close() {
    if (this.transform != null) {
      this.transform.close();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void convertsRestrictedScalars(boolean bytes) throws IOException {
    configure();
    assertMeasurement(transform.apply(record("valid.xml", bytes)));
  }

  @ParameterizedTest(name = "{0}, bytes={1}")
  @CsvSource({
      "invalid-pattern.xml,false", "invalid-pattern.xml,true",
      "invalid-length.xml,false", "invalid-length.xml,true",
      "invalid-min.xml,false", "invalid-min.xml,true",
      "invalid-max.xml,false", "invalid-max.xml,true",
      "invalid-required.xml,false", "invalid-required.xml,true",
      "invalid-cardinality.xml,false", "invalid-cardinality.xml,true"
  })
  public void validatesEveryDocument(String filename, boolean bytes) throws IOException {
    configure();
    assertMeasurement(transform.apply(record("valid.xml", bytes)));
    SinkRecord invalid = record(filename, bytes);
    assertThrows(DataException.class, () -> transform.apply(invalid), filename);
    assertMeasurement(transform.apply(record("valid.xml", bytes)));
  }

  private static URL resource(String filename) {
    return Objects.requireNonNull(PublicRestrictionTest.class.getResource(
        "public/restrictions/" + filename), filename);
  }

  private static SinkRecord record(String filename, boolean bytes) throws IOException {
    byte[] xml;
    try (java.io.InputStream input = resource(filename).openStream()) {
      xml = input.readAllBytes();
    }
    return new SinkRecord("restrictions", 0, null, null,
        bytes ? Schema.BYTES_SCHEMA : Schema.STRING_SCHEMA,
        bytes ? xml : new String(xml, StandardCharsets.UTF_8), 0L);
  }

  private static void assertMeasurement(SinkRecord record) {
    Struct value = assertInstanceOf(Struct.class, record.value());
    ConnectSchema.validateValue(record.valueSchema(), value);
    assertEquals(Schema.Type.STRUCT, record.valueSchema().type());
    assertEquals(record.valueSchema(), value.schema());
    assertEquals(2, value.schema().fields().size());
    assertEquals(Schema.Type.STRING, value.schema().field("myZipCode").schema().type());
    assertEquals(Schema.Type.INT32, value.schema().field("myDistance").schema().type());
    assertEquals("02139", value.getString("myZipCode"));
    assertEquals(Integer.valueOf(42), value.getInt32("myDistance"));
  }
}
