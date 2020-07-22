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

import com.google.common.io.Files;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.ConnectSchema;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class WBAFromXmlTest {

  WBAFromXml.Value transformValue;
  WBAFromXml.Key transformKey;

  @BeforeEach
  public void before() throws MalformedURLException {
    File file = new File("src/test/resources/com/github/jcustenborder/kafka/connect/transform/xml/books.xsd");
    this.transformValue = new WBAFromXml.Value();
    this.transformKey = new WBAFromXml.Key();
    Map<String,String> testConfigs = new HashMap<>();
    testConfigs.put(WBAFromXmlConfig.SCHEMA_PATH_CONFIG,file.getAbsoluteFile().toURL().toString());
    testConfigs.put(WBAFromXmlConfig.REROUTE_ON_FAIL_TOPIC_CONFIG,"DLQTopic");
    this.transformValue.configure(testConfigs);
    this.transformKey.configure(testConfigs);
  }

  @AfterEach
  public void after() {
    this.transformValue.close();
    this.transformKey.close();
  }

  @Test
  public void apply() throws IOException {
    final byte[] input = Files.toByteArray(new File("src/test/resources/com/github/jcustenborder/kafka/connect/transform/xml/books.xml"));
    final ConnectRecord inputRecord = new SinkRecord(
        "test",
        1,
        Schema.BYTES_SCHEMA,
        input,
        Schema.BYTES_SCHEMA,
        input,
        new Date().getTime()
    );

    ConnectRecord recordValTransform = this.transformValue.apply(inputRecord);
    Assert.assertEquals(Struct.class,recordValTransform.value().getClass());
    Assert.assertEquals(ConnectSchema.class,recordValTransform.valueSchema().getClass());

    ConnectRecord recordKeyTransform = this.transformKey.apply(inputRecord);
    Assert.assertEquals(Struct.class, recordKeyTransform.key().getClass());
    Assert.assertEquals(ConnectSchema.class, recordKeyTransform.keySchema().getClass());
  }

  @Test
  public void applyReroute() {
    final byte[] input = "fakeStructure".getBytes();
    final ConnectRecord inputRecord = new SinkRecord(
            "test",
            1,
            Schema.BYTES_SCHEMA,
            input,
            Schema.BYTES_SCHEMA,
            input,
            new Date().getTime()
    );

    ConnectRecord recordValTransform = this.transformValue.apply(inputRecord);
    Assert.assertEquals("DLQTopic",recordValTransform.topic());
    Assert.assertEquals(Struct.class,recordValTransform.value().getClass());
    Assert.assertEquals(ConnectSchema.class,recordValTransform.valueSchema().getClass());

    ConnectRecord recordKeyTransform = this.transformKey.apply(inputRecord);
    Assert.assertEquals("DLQTopic",recordKeyTransform.topic());
    Assert.assertEquals(Struct.class,recordKeyTransform.key().getClass());
    Assert.assertEquals(ConnectSchema.class,recordKeyTransform.valueSchema().getClass());

  }


}
