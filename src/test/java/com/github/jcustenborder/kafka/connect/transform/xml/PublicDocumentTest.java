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
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PublicDocumentTest {
  private static final Path FIXTURE = Paths.get(
      "src/test/resources/com/github/jcustenborder/kafka/connect/transform/xml/public/document");
  private static final String PACKAGE = "com.github.jcustenborder.kafka.connect.transform.xml.document";
  private FromXml.Value<SinkRecord> transform;

  private void configure() {
    FromXml.Value<SinkRecord> candidate = new FromXml.Value<>();
    candidate.configure(Map.of(
        FromXmlConfig.SCHEMA_PATH_CONFIG, FIXTURE.resolve("invoice.xsd").toUri().toString(),
        FromXmlConfig.PACKAGE_CONFIG, PACKAGE));
    transform = candidate;
  }

  @AfterEach
  public void close() {
    if (transform != null) {
      transform.close();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void mapsNestedCustomerAndDocumentAttribute(boolean bytes) throws IOException {
    configure();
    SinkRecord result = apply(Files.readString(FIXTURE.resolve("invoice.xml")), bytes);
    Struct invoice = assertInstanceOf(Struct.class, result.value());
    ConnectSchema.validateValue(result.valueSchema(), invoice);
    assertEquals(invoice.schema(), result.valueSchema());
    assertEquals(Schema.Type.STRUCT, result.valueSchema().type());
    assertEquals(PACKAGE + ".Invoice", result.valueSchema().name());
    assertEquals(3, invoice.schema().fields().size());
    assertEquals(Schema.Type.STRING, invoice.schema().field("id").schema().type());
    assertEquals("INV-1007", invoice.getString("id"));
    Struct header = invoice.getStruct("header");
    assertEquals(Schema.Type.STRUCT, invoice.schema().field("header").schema().type());
    assertEquals(PACKAGE + ".InvoiceHeader", header.schema().name());
    assertEquals(1, header.schema().fields().size());
    Struct customer = header.getStruct("customer");
    assertEquals(Schema.Type.STRUCT, header.schema().field("customer").schema().type());
    assertEquals(PACKAGE + ".Customer", customer.schema().name());
    assertEquals(3, customer.schema().fields().size());
    assertEquals(Schema.Type.STRING, customer.schema().field("firstName").schema().type());
    assertEquals(Schema.Type.STRING, customer.schema().field("lastName").schema().type());
    assertEquals(Schema.Type.STRING, customer.schema().field("customerNumber").schema().type());
    assertEquals("Alice", customer.getString("firstName"));
    assertEquals("Martin", customer.getString("lastName"));
    assertEquals("C-0042", customer.getString("customerNumber"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void preservesTypedInvoiceLinesAndAttributesInDocumentOrder(boolean bytes) throws IOException {
    configure();
    SinkRecord result = apply(Files.readString(FIXTURE.resolve("invoice.xml")), bytes);
    Struct invoice = assertInstanceOf(Struct.class, result.value());
    ConnectSchema.validateValue(result.valueSchema(), invoice);
    Schema linesSchema = invoice.schema().field("invoiceLine").schema();
    assertEquals(Schema.Type.ARRAY, linesSchema.type());
    assertEquals(Schema.Type.STRUCT, linesSchema.valueSchema().type());
    assertEquals(PACKAGE + ".InvoiceLine", linesSchema.valueSchema().name());
    assertEquals(4, linesSchema.valueSchema().fields().size());
    assertEquals(Schema.Type.INT32, linesSchema.valueSchema().field("productCode").schema().type());
    assertEquals(Schema.Type.INT32, linesSchema.valueSchema().field("quantity").schema().type());
    assertEquals(Schema.Type.STRING, linesSchema.valueSchema().field("description").schema().type());
    assertEquals(Schema.Type.STRING, linesSchema.valueSchema().field("warehouse").schema().type());
    List<Struct> lines = invoice.getArray("invoiceLine");
    assertEquals(2, lines.size());
    assertEquals(linesSchema.valueSchema(), lines.get(0).schema());
    assertEquals(linesSchema.valueSchema(), lines.get(1).schema());
    assertEquals(Integer.valueOf(501), lines.get(0).getInt32("productCode"));
    assertEquals(Integer.valueOf(3), lines.get(0).getInt32("quantity"));
    assertEquals("Notebooks & pads", lines.get(0).getString("description"));
    assertEquals("EAST", lines.get(0).getString("warehouse"));
    assertEquals(Integer.valueOf(204), lines.get(1).getInt32("productCode"));
    assertEquals(Integer.valueOf(12), lines.get(1).getInt32("quantity"));
    assertEquals("Blue pens", lines.get(1).getString("description"));
    assertEquals("WEST", lines.get(1).getString("warehouse"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void rejectsInvoiceLineMissingRequiredDescription(boolean bytes) throws IOException {
    configure();
    String invalid = Files.readString(FIXTURE.resolve("invoice.xml"))
        .replace("<description>Notebooks &amp; pads</description>", "");
    assertThrows(DataException.class, () -> apply(invalid, bytes));
  }

  private SinkRecord apply(String xml, boolean bytes) {
    return transform.apply(new SinkRecord("invoices", 0, null, null,
        bytes ? Schema.BYTES_SCHEMA : Schema.STRING_SCHEMA,
        bytes ? xml.getBytes(StandardCharsets.UTF_8) : xml, 0L));
  }
}
