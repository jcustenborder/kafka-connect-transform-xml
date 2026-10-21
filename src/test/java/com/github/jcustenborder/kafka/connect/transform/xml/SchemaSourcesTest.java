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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SchemaSourcesTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void fetchFailuresIdentifySchemaAndPreserveCause(boolean failOnOpen) throws Exception {
    IOException failure = new SocketTimeoutException("Read timed out");
    URL url = new URL(null, "http://example.invalid/schema.xsd", new URLStreamHandler() {
      @Override
      protected URLConnection openConnection(URL requested) throws IOException {
        if (failOnOpen) {
          throw failure;
        }
        return new URLConnection(requested) {
          @Override
          public void connect() {
          }

          @Override
          public InputStream getInputStream() throws IOException {
            throw failure;
          }
        };
      }
    });
    IOException error = assertThrows(IOException.class, () -> new SchemaSources(List.of(url)));
    assertTrue(error.getMessage().contains(url.toExternalForm()), error.getMessage());
    assertSame(failure, error.getCause());
  }
}
