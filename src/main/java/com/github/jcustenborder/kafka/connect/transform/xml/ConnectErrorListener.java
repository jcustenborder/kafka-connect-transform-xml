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

import com.sun.tools.xjc.api.ErrorListener;
import org.slf4j.Logger;
import org.xml.sax.SAXParseException;

class ConnectErrorListener implements ErrorListener {
  final Logger log;

  ConnectErrorListener(Logger log) {
    this.log = log;
  }

  @Override
  public void error(SAXParseException e) {
    log.error("Error", e);
  }

  @Override
  public void fatalError(SAXParseException e) {
    log.error("fatalError", e);
  }

  @Override
  public void warning(SAXParseException e) {
    log.error("warning", e);
  }

  @Override
  public void info(SAXParseException e) {
    log.info("info", e);
  }
}
