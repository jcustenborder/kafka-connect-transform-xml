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
import com.github.jcustenborder.kafka.connect.utils.transformation.BaseKeyValueTransformation;
import com.github.jcustenborder.kafka.connect.xml.Connectable;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;

@Title("FromXML")
@Description("This transformation is used to rename fields in the key of an input struct based on a regular expression and a replacement string.")
@DocumentationTip("This transformation is used to manipulate fields in the Key of the record.")
public abstract class FromXml<R extends ConnectRecord<R>> extends BaseKeyValueTransformation<R> {
  private static final Logger log = LoggerFactory.getLogger(FromXml.class);
  FromXmlConfig config;
  JAXBContext context;
  Unmarshaller unmarshaller;
  XSDCompiler compiler;

  protected FromXml(boolean isKey) {
    super(isKey);
  }

  @Override
  public ConfigDef config() {
    return FromXmlConfig.config();
  }

  @Override
  public void close() {
    try {
      this.compiler.close();
    } catch (IOException e) {
      log.error("Exception thrown", e);
    }
  }

  @Override
  protected SchemaAndValue processString(R record, org.apache.kafka.connect.data.Schema inputSchema, String input) {
    try (Reader reader = new StringReader(input)) {
      Object element = this.unmarshaller.unmarshal(reader);
      return schemaAndValue(element);
    } catch (IOException | JAXBException e) {
      throw new DataException("Exception thrown while processing xml", e);
    }
  }

  @Override
  protected SchemaAndValue processBytes(R record, org.apache.kafka.connect.data.Schema inputSchema, byte[] input) {
    try (InputStream inputStream = new ByteArrayInputStream(input)) {
      try (Reader reader = new InputStreamReader(inputStream)) {
        Object element = this.unmarshaller.unmarshal(reader);
        return schemaAndValue(element);
      }
    } catch (IOException | JAXBException e) {
      throw new DataException("Exception thrown while processing xml", e);
    }
  }

  private SchemaAndValue schemaAndValue(Object element) {
    final Struct struct;
    if (element instanceof Connectable) {
      Connectable connectable = (Connectable) element;
      struct = connectable.toStruct();
    } else if (element instanceof JAXBElement) {
      JAXBElement jaxbElement = (JAXBElement) element;

      if (jaxbElement.getValue() instanceof Connectable) {
        Connectable connectable = (Connectable) jaxbElement.getValue();
        struct = connectable.toStruct();
      } else {
        throw new DataException(
            String.format(
                "%s does not implement Connectable",
                jaxbElement.getValue().getClass()
            )
        );
      }
    } else {
      throw new DataException(
          String.format("%s is not a supported type", element.getClass())
      );
    }
    return new SchemaAndValue(struct.schema(), struct);
  }

  @Override
  public void configure(Map<String, ?> settings) {
    this.config = new FromXmlConfig(settings);
    this.compiler = new XSDCompiler(this.config);

    try {
      this.context = compiler.compileContext();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    try {
      this.unmarshaller = context.createUnmarshaller();
    } catch (JAXBException e) {
      throw new IllegalStateException(e);
    }
  }


  public static class Key<R extends ConnectRecord<R>> extends FromXml<R> {

    protected Key() {
      super(true);
    }

    @Override
    public R apply(R r) {
      final SchemaAndValue transformed = process(r, new SchemaAndValue(r.keySchema(), r.key()));

      return r.newRecord(
          r.topic(),
          r.kafkaPartition(),
          transformed.schema(),
          transformed.value(),
          r.valueSchema(),
          r.value(),
          r.timestamp()
      );
    }
  }

  public static class Value<R extends ConnectRecord<R>> extends FromXml<R> {
    protected Value() {
      super(false);
    }

    @Override
    public R apply(R r) {
      final SchemaAndValue transformed = process(r, new SchemaAndValue(r.valueSchema(), r.value()));

      return r.newRecord(
          r.topic(),
          r.kafkaPartition(),
          r.keySchema(),
          r.key(),
          transformed.schema(),
          transformed.value(),
          r.timestamp()
      );
    }
  }
}
