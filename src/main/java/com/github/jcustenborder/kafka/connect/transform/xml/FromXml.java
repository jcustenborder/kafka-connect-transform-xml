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

import com.github.jcustenborder.kafka.connect.utils.config.Description;
import com.github.jcustenborder.kafka.connect.utils.config.DocumentationTip;
import com.github.jcustenborder.kafka.connect.utils.config.Title;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.ConnectSchema;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.transforms.Transformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Converts XSD-validated XML strings or bytes into Connect Structs.
 * @param <R> Kafka Connect record type
 */
@Title("FromXML")
@Description("This transformation is used to read XML data stored as bytes or a string and convert " +
    "the XML to a structure that is strongly typed in connect. This allows data to be converted from XML " +
    "and stored as AVRO in a topic for example. ")
@DocumentationTip("XML schemas can be much more complex that what can be expressed in a Kafka " +
    "Connect struct. Elements that can be expressed as an anyType or something similar cannot easily " +
    "be used to infer type information.")
public abstract class FromXml<R extends ConnectRecord<R>> implements Transformation<R> {
  private static final Logger log = LoggerFactory.getLogger(FromXml.class);
  private final boolean isKey;
  private XmlSchema mapping;

  /**
   * Selects the side of the record to transform.
   * @param isKey true to transform the key, false to transform the value
   */
  protected FromXml(boolean isKey) {
    this.isKey = isKey;
  }

  @Override
  public ConfigDef config() {
    return FromXmlConfig.config();
  }

  @Override
  public void close() {
    this.mapping = null;
  }

  @Override
  public R apply(R record) {
    if (mapping == null) {
      throw new IllegalStateException("The XML transform is not configured or has been closed");
    }
    Object input = isKey ? record.key() : record.value();
    if (input == null) {
      return record;
    }
    Schema inputSchema = isKey ? record.keySchema() : record.valueSchema();
    if (inputSchema != null && inputSchema.type() != Schema.Type.STRING
        && inputSchema.type() != Schema.Type.BYTES) {
      throw new DataException("Expected a STRING or BYTES input schema, got " + inputSchema.type());
    }
    if (inputSchema != null) {
      ConnectSchema.validateValue(inputSchema, input);
    }
    final InputSource source;
    if (input instanceof String) {
      source = new InputSource(new StringReader((String) input));
    } else if (input instanceof byte[]) {
      source = new InputSource(new ByteArrayInputStream((byte[]) input));
    } else if (input instanceof ByteBuffer) {
      ByteBuffer buffer = ((ByteBuffer) input).duplicate();
      byte[] bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      source = new InputSource(new ByteArrayInputStream(bytes));
    } else {
      throw new DataException("Expected STRING or BYTES XML input, got " + input.getClass().getName());
    }
    SchemaAndValue output = mapping.read(source);
    return record.newRecord(record.topic(), record.kafkaPartition(),
        isKey ? output.schema() : record.keySchema(), isKey ? output.value() : record.key(),
        isKey ? record.valueSchema() : output.schema(), isKey ? record.value() : output.value(),
        record.timestamp(), record.headers());
  }

  @Override
  public void configure(Map<String, ?> settings) {
    FromXmlConfig config = new FromXmlConfig(settings);
    this.mapping = new XmlSchema(config);
    if (config.schemaUrls.isEmpty()) {
      log.info("Configured XML-to-Struct mapping from inline schema.content");
    } else {
      log.info("Configured XML-to-Struct mapping from {} XSD URL(s)", config.schemaUrls.size());
    }
  }


  /**
   * XML-to-Struct transformation for record keys.
   * @param <R> Kafka Connect record type
   */
  public static class Key<R extends ConnectRecord<R>> extends FromXml<R> {
    /** Creates the key transformation. */
    public Key() {
      super(true);
    }
  }

  /**
   * XML-to-Struct transformation for record values.
   * @param <R> Kafka Connect record type
   */
  public static class Value<R extends ConnectRecord<R>> extends FromXml<R> {
    /** Creates the value transformation. */
    public Value() {
      super(false);
    }
  }
}
