/**
 * Copyright © 2017 Jeremy Custenborder (jcustenborder@gmail.com)
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

import org.apache.kafka.common.record.TimestampType;
import org.apache.kafka.connect.data.ConnectSchema;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FromXmlTest {

  private FromXml<SinkRecord> transform;

  private void configure(boolean key) {
    FromXml<SinkRecord> candidate = key ? new FromXml.Key<>() : new FromXml.Value<>();
    candidate.configure(Collections.singletonMap(FromXmlConfig.SCHEMA_PATH_CONFIG,
        Paths.get("src/test/resources/com/github/jcustenborder/kafka/connect/transform/xml/books.xsd")
            .toUri().toString()));
    this.transform = candidate;
  }

  @AfterEach
  public void after() {
    if (this.transform != null) {
      this.transform.close();
    }
  }

  @Test
  public void apply() throws IOException {
    configure(false);
    byte[] input = Files.readAllBytes(Paths.get(
        "src/test/resources/com/github/jcustenborder/kafka/connect/transform/xml/books.xml"));
    SinkRecord record = transform.apply(record(false, Schema.BYTES_SCHEMA, input));
    assertBooks(record.valueSchema(), record.value());
  }

  @ParameterizedTest
  @CsvSource({"true,true,true", "true,true,false", "true,false,true", "true,false,false",
      "false,true,true", "false,true,false", "false,false,true", "false,false,false"})
  public void convertsSelectedSideAndPreservesRecord(boolean key, boolean bytes, boolean schemaful)
      throws IOException {
    configure(key);
    byte[] xml = Files.readAllBytes(Paths.get(
        "src/test/resources/com/github/jcustenborder/kafka/connect/transform/xml/books.xml"));
    Object input = bytes ? xml : new String(xml, StandardCharsets.UTF_8);
    Schema schema = schemaful ? (bytes ? Schema.BYTES_SCHEMA : Schema.STRING_SCHEMA) : null;
    SinkRecord original = record(key, schema, input);
    original.headers().addString("trace-id", "abc");
    SinkRecord result = transform.apply(original);

    assertBooks(key ? result.keySchema() : result.valueSchema(), key ? result.key() : result.value());
    assertSame(key ? original.valueSchema() : original.keySchema(),
        key ? result.valueSchema() : result.keySchema());
    assertSame(key ? original.value() : original.key(), key ? result.value() : result.key());
    assertSame(input, key ? original.key() : original.value(), "Input record must not be modified");
    assertEquals(original.topic(), result.topic());
    assertEquals(original.kafkaPartition(), result.kafkaPartition());
    assertEquals(original.kafkaOffset(), result.kafkaOffset());
    assertEquals(original.timestamp(), result.timestamp());
    assertEquals(original.timestampType(), result.timestampType());
    assertEquals("abc", result.headers().lastWithName("trace-id").value());
  }

  @ParameterizedTest
  @CsvSource({"true,false", "false,false", "true,true", "false,true"})
  public void passesThroughNullSelectedSide(boolean key, boolean schemaful) {
    configure(key);
    Schema schema = schemaful ? Schema.OPTIONAL_STRING_SCHEMA : null;
    SinkRecord original = record(key, schema, null);
    SinkRecord result = transform.apply(original);
    assertSame(original, result, "A null selected side should pass through without copying the record");
    assertNull(key ? result.key() : result.value());
    assertEquals(schema, key ? result.keySchema() : result.valueSchema());
    assertSame(key ? original.value() : original.key(), key ? result.value() : result.key());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "<books>", "<wrong/>", "<books xmlns='wrong:namespace'/>"})
  public void rejectsMalformedXmlOrUnknownRoot(String xml) {
    configure(false);
    assertThrows(DataException.class, () -> transform.apply(record(false, Schema.STRING_SCHEMA, xml)));
  }

  @Test
  public void honorsDeclaredByteEncoding() throws IOException {
    configure(false);
    String xml = new String(Files.readAllBytes(Paths.get(
        "src/test/resources/com/github/jcustenborder/kafka/connect/transform/xml/books.xml")),
        StandardCharsets.UTF_8)
        .replace("<?xml version=\"1.0\"?>", "<?xml version=\"1.0\" encoding=\"UTF-16\"?>")
        .replace("Writer", "\u00c9crivain");
    SinkRecord result = transform.apply(record(false, Schema.BYTES_SCHEMA,
        xml.getBytes(StandardCharsets.UTF_16)));
    List<Struct> books = ((Struct) result.value()).getArray("book");
    assertEquals("\u00c9crivain", books.get(0).getString("author"));
  }

  @Test
  public void rejectsInvalidNumericValue() throws IOException {
    configure(false);
    String xml = new String(Files.readAllBytes(Paths.get(
        "src/test/resources/com/github/jcustenborder/kafka/connect/transform/xml/books.xml")),
        StandardCharsets.UTF_8).replace("44.95", "not-a-number");
    assertThrows(DataException.class, () -> transform.apply(record(false, Schema.STRING_SCHEMA, xml)));
  }

  @Test
  public void dateValuesUseConnectUtcRepresentation() throws IOException {
    configure(false);
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
      byte[] input = Files.readAllBytes(Paths.get(
          "src/test/resources/com/github/jcustenborder/kafka/connect/transform/xml/books.xml"));
      Struct root = (Struct) transform.apply(record(false, Schema.BYTES_SCHEMA, input)).value();
      List<Struct> books = root.getArray("book");
      for (Struct book : books) {
        assertEquals(LocalDate.of(2000, 10, 1).toEpochDay(),
            org.apache.kafka.connect.data.Date.fromLogical(
                book.schema().field("pub_date").schema(), (java.util.Date) book.get("pub_date")));
      }
    } finally {
      TimeZone.setDefault(original);
    }
  }

  @Test
  public void rejectsMissingRequiredElement() throws IOException {
    configure(false);
    String xml = new String(Files.readAllBytes(Paths.get(
        "src/test/resources/com/github/jcustenborder/kafka/connect/transform/xml/books.xml")),
        StandardCharsets.UTF_8).replace("<author>Writer</author>", "");
    assertThrows(DataException.class, () -> transform.apply(record(false, Schema.STRING_SCHEMA, xml)));
  }

  @Test
  public void rejectsDoctypeWithExternalEntity() throws IOException {
    configure(false);
    String external = Paths.get(
        "src/test/resources/com/github/jcustenborder/kafka/connect/transform/xml/external-entity.txt")
        .toUri().toString();
    String xml = new String(Files.readAllBytes(Paths.get(
        "src/test/resources/com/github/jcustenborder/kafka/connect/transform/xml/books.xml")),
        StandardCharsets.UTF_8)
        .replace("<?xml version=\"1.0\"?>", "<!DOCTYPE books [<!ENTITY external SYSTEM '"
            + external + "'>]>")
        .replace("Writer", "&external;");
    assertThrows(DataException.class, () -> transform.apply(record(false, Schema.STRING_SCHEMA, xml)));
  }

  private static SinkRecord record(boolean key, Schema schema, Object input) {
    return new SinkRecord("books", 2, key ? schema : Schema.STRING_SCHEMA, key ? input : "keep-key",
        key ? Schema.STRING_SCHEMA : schema, key ? "keep-value" : input,
        42L, 123456789L, TimestampType.CREATE_TIME);
  }

  private static void assertBooks(Schema schema, Object value) {
    assertTrue(value instanceof Struct);
    Struct root = (Struct) value;
    assertEquals(schema, root.schema());
    ConnectSchema.validateValue(schema, root);
    assertEquals("com.github.jcustenborder.kafka.connect.transform.xml.model.BooksForm", schema.name());
    assertEquals(Schema.Type.STRUCT, schema.type());
    assertTrue(schema.isOptional());
    assertEquals(1, schema.fields().size());
    Schema array = schema.field("book").schema();
    assertEquals(Schema.Type.ARRAY, array.type());
    assertTrue(array.isOptional());
    Schema bookSchema = array.valueSchema();
    assertEquals("com.github.jcustenborder.kafka.connect.transform.xml.model.BookForm", bookSchema.name());
    assertTrue(bookSchema.isOptional());
    assertEquals(Arrays.asList("author", "title", "genre", "price", "pub_date", "review", "id"),
        bookSchema.fields().stream().map(field -> field.name()).collect(Collectors.toList()));
    for (String field : Arrays.asList("author", "title", "genre", "review")) {
      assertEquals(Schema.STRING_SCHEMA, bookSchema.field(field).schema());
    }
    assertEquals(Schema.OPTIONAL_STRING_SCHEMA, bookSchema.field("id").schema());
    // Characterize the existing primitive-field optionality, even though the XSD requires price.
    assertEquals(Schema.OPTIONAL_FLOAT32_SCHEMA, bookSchema.field("price").schema());
    assertEquals(org.apache.kafka.connect.data.Date.SCHEMA, bookSchema.field("pub_date").schema());
    assertFalse(bookSchema.field("pub_date").schema().isOptional());
    List<Struct> books = root.getArray("book");
    assertNotNull(books);
    assertEquals(2, books.size());
    assertBook(bookSchema, books.get(0), "bk001", "Writer", "The First Book", "Fiction", 44.95f,
        "An amazing story of nothing.");
    assertBook(bookSchema, books.get(1), "bk002", "Poet", "The Poet's First Poem", "Poem", 24.95f,
        "Least poetic poems.");
  }

  private static void assertBook(Schema schema, Struct book, String id, String author, String title,
                                 String genre, float price, String review) {
    assertEquals(schema, book.schema());
    assertEquals(id, book.getString("id"));
    assertEquals(author, book.getString("author"));
    assertEquals(title, book.getString("title"));
    assertEquals(genre, book.getString("genre"));
    assertEquals(price, book.getFloat32("price").floatValue(), 0.00001f);
    assertEquals(review, book.getString("review"));
    assertNotNull(book.get("pub_date"));
  }

}
