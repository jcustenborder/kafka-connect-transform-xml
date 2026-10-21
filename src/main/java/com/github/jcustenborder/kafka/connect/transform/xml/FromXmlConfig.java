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

import com.github.jcustenborder.kafka.connect.utils.config.ConfigKeyBuilder;
import com.github.jcustenborder.kafka.connect.utils.config.ConfigUtils;
import com.github.jcustenborder.kafka.connect.utils.config.validators.ValidUrl;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;

import java.net.URL;
import java.util.List;
import java.util.Map;

class FromXmlConfig extends AbstractConfig {

  public static final String SCHEMA_PATH_CONFIG = "schema.path";
  public static final String SCHEMA_CONTENT_CONFIG = "schema.content";
  public static final String PACKAGE_CONFIG = "package";
  static final String SCHEMA_PATH_DOC = "URLs to the schemas to load (file, http, https). Configure exactly one of schema.path or schema.content.";
  static final String SCHEMA_CONTENT_DOC = "Inline XSD document. Configure exactly one of schema.path or schema.content. "
      + "Imports/includes must use absolute URLs because inline content has no base URL.";
  static final String PACKAGE_DOC = "Name prefix for generated Connect schemas; no Java classes are generated.";
  public final List<URL> schemaUrls;
  public final String schemaContent;
  public final String schemaPackage;

  public FromXmlConfig(Map<?, ?> originals) {
    super(config(), originals);
    this.schemaUrls = ConfigUtils.urls(this, SCHEMA_PATH_CONFIG);
    this.schemaContent = getString(SCHEMA_CONTENT_CONFIG);
    if (this.schemaUrls.isEmpty() == (this.schemaContent == null || this.schemaContent.isBlank())) {
      throw new ConfigException("Exactly one of schema.path or schema.content must be configured");
    }
    this.schemaPackage = getString(PACKAGE_CONFIG);
  }

  public static ConfigDef config() {
    return new ConfigDef()
        .define(
            ConfigKeyBuilder.of(SCHEMA_PATH_CONFIG, ConfigDef.Type.LIST)
                .documentation(SCHEMA_PATH_DOC)
                .importance(ConfigDef.Importance.HIGH)
                .validator(new ValidUrl())
                .defaultValue(List.of())
                .build()
        ).define(
            ConfigKeyBuilder.of(SCHEMA_CONTENT_CONFIG, ConfigDef.Type.STRING)
                .documentation(SCHEMA_CONTENT_DOC)
                .importance(ConfigDef.Importance.HIGH)
                .defaultValue("")
                .build()
        ).define(
            ConfigKeyBuilder.of(PACKAGE_CONFIG, ConfigDef.Type.STRING)
                .documentation(PACKAGE_DOC)
                .importance(ConfigDef.Importance.HIGH)
                .validator(new ConfigDef.NonEmptyString())
                .defaultValue(FromXmlConfig.class.getPackage().getName() + ".model")
                .build()
        );
  }


}
