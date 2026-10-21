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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScalarMappingTest {
  private FromXml.Value<SinkRecord> transform;

  private void configure(String schema) {
    FromXml.Value<SinkRecord> candidate = new FromXml.Value<>();
    candidate.configure(Collections.singletonMap(FromXmlConfig.SCHEMA_PATH_CONFIG,
        Paths.get("src/test/resources/com/github/jcustenborder/kafka/connect/transform/xml/" + schema)
            .toUri().toString()));
    transform = candidate;
  }

  @AfterEach
  public void close() {
    if (transform != null) {
      transform.close();
    }
  }

  static Stream<Arguments> scalars() {
    return Stream.of(
        Arguments.of("text", "caf\u00e9 &amp; tea", Schema.Type.STRING, "caf\u00e9 & tea"),
        Arguments.of("flag", "true", Schema.Type.BOOLEAN, true),
        Arguments.of("flag", "0", Schema.Type.BOOLEAN, false),
        Arguments.of("tiny", "-128", Schema.Type.INT8, (byte) -128),
        Arguments.of("small", "32767", Schema.Type.INT16, (short) 32767),
        Arguments.of("number", "2147483647", Schema.Type.INT32, Integer.MAX_VALUE),
        Arguments.of("large", "9223372036854775807", Schema.Type.INT64, Long.MAX_VALUE),
        Arguments.of("single", "1.25", Schema.Type.FLOAT32, 1.25f),
        Arguments.of("doubleValue", "2.5", Schema.Type.FLOAT64, 2.5d));
  }

  @ParameterizedTest
  @MethodSource("scalars")
  public void mapsXsdScalars(String field, String xmlValue, Schema.Type type, Object expected) {
    configure("scalars.xsd");
    Struct result = apply("<scalars id='item'><" + field + ">" + xmlValue + "</" + field + "></scalars>");
    assertEquals(type, result.schema().field(field).schema().type());
    assertTrue(result.schema().field(field).schema().isOptional());
    assertEquals(expected, result.get(field));
    assertEquals("item", result.getString("id"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "<text/>"})
  public void distinguishesMissingAndEmptyString(String content) {
    configure("scalars.xsd");
    Struct result = apply("<scalars>" + content + "</scalars>");
    assertEquals(content.equals("<text/>") ? "" : null, result.get("text"));
    assertNull(result.get("number"));
    assertNull(result.get("id"));
  }

  @Test
  public void mapsExplicitNilToNull() {
    configure("nillable.xsd");
    Struct result = apply("<scalars xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>"
        + "<text xsi:nil='true'/></scalars>");
    assertTrue(result.schema().field("text").schema().isOptional());
    assertNull(result.get("text"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"<tiny>128</tiny>", "<small>32768</small>",
      "<number>2147483648</number>", "<large>9223372036854775808</large>"})
  public void rejectsNumericOverflow(String content) {
    configure("scalars.xsd");
    assertThrows(DataException.class, () -> apply("<scalars>" + content + "</scalars>"));
  }

  private Struct apply(String xml) {
    SinkRecord input = new SinkRecord("scalars", 0, null, null, Schema.STRING_SCHEMA, xml, 0L);
    SinkRecord output = transform.apply(input);
    Struct value = assertInstanceOf(Struct.class, output.value());
    ConnectSchema.validateValue(output.valueSchema(), value);
    return value;
  }
}
