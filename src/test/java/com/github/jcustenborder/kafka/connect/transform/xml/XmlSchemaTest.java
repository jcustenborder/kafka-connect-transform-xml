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

import org.apache.kafka.connect.errors.ConnectException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class XmlSchemaTest {
  @Test
  public void parserSetupFailureIsNotReportedAsBadRecordData() throws Exception {
    XmlSchema schema = new XmlSchema(new FromXmlConfig(Map.of(FromXmlConfig.SCHEMA_PATH_CONFIG,
        Path.of("src/test/resources/com/github/jcustenborder/kafka/connect/transform/xml/books.xsd")
            .toUri().toString())));
    ParserConfigurationException failure = new ParserConfigurationException("Parser setup failed");
    DocumentBuilderFactory factory = mock(DocumentBuilderFactory.class);
    when(factory.newDocumentBuilder()).thenThrow(failure);
    // The fixed JDK factory cannot naturally fail on this JDK; replace only its setup boundary.
    try (MockedStatic<DocumentBuilderFactory> defaults = mockStatic(DocumentBuilderFactory.class)) {
      defaults.when(DocumentBuilderFactory::newDefaultInstance).thenReturn(factory);
      ConnectException error = assertThrowsExactly(ConnectException.class, () -> schema.read(
          new InputSource(new StringReader("<books xmlns='urn:books'/>"))));
      assertSame(failure, error.getCause());
    }
  }
}
