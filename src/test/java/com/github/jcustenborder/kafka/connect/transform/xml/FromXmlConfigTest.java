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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FromXmlConfigTest {
  @Test
  public void requiresExactlyOneSchemaSource() {
    ConfigException error = assertThrows(ConfigException.class,
        () -> new FromXmlConfig(Collections.emptyMap()));
    assertTrue(error.getMessage().contains("schema.path"));
    assertTrue(error.getMessage().contains("schema.content"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\n\t"})
  public void blankContentDoesNotSupplyASchema(String content) {
    assertThrows(ConfigException.class, () -> new FromXmlConfig(Map.of("schema.content", content)));
    new FromXmlConfig(Map.of("schema.content", content, "schema.path", "https://example.com/root.xsd"));
  }

  @Test
  public void rejectsBothSources() {
    ConfigException error = assertThrows(ConfigException.class, () -> new FromXmlConfig(Map.of(
        "schema.path", "https://example.com/root.xsd", "schema.content", "<schema/>")));
    assertTrue(error.getMessage().contains("schema.path"));
    assertTrue(error.getMessage().contains("schema.content"));
  }

  @Test
  public void rejectsMalformedSchemaUrl() {
    assertThrows(ConfigException.class, () -> new FromXmlConfig(Collections.singletonMap(
        FromXmlConfig.SCHEMA_PATH_CONFIG, "not a URL")));
  }

  @Test
  public void configurationFailureIdentifiesMissingSchemaAndPreservesCause(@TempDir Path directory) throws IOException {
    String url = directory.resolve("missing.xsd").toUri().toURL().toExternalForm();
    ConfigException error = assertThrows(ConfigException.class, () ->
        new FromXml.Value<>().configure(Collections.singletonMap(FromXmlConfig.SCHEMA_PATH_CONFIG, url)));
    assertTrue(error.getMessage().contains(url), error.getMessage());
    IOException fetch = assertInstanceOf(IOException.class, error.getCause());
    assertInstanceOf(FileNotFoundException.class, fetch.getCause());
  }
}
