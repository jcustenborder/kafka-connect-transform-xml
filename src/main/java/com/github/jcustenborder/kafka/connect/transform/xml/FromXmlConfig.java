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
  public static final String PACKAGE_CONFIG = "package";
  public static final String XJC_OPTIONS_STRICT_CHECK_CONFIG = "xjc.options.strict.check.enabled";
  public static final String XJC_OPTIONS_AUTOMATIC_NAME_CONFLICT_RESOLUTION_ENABLED_CONFIG = "xjc.options.automatic.name.conflict.resolution.enabled";
  public static final String XJC_OPTIONS_VERBOSE_CONFIG = "xjc.options.verbose.enabled";
  static final String SCHEMA_PATH_DOC = "Urls to the schemas to load. http and https paths are supported";
  static final String PACKAGE_DOC = "Name prefix for generated Connect schemas; no Java classes are generated.";
  static final String XJC_OPTIONS_STRICT_CHECK_DOC = "Deprecated and ignored. Every XML document is validated against its XSD.";
  static final String XJC_OPTIONS_AUTOMATIC_NAME_CONFLICT_RESOLUTION_ENABLED_DOC = "Deprecated and ignored. Ambiguous Connect field/schema names are rejected.";
  static final String XJC_OPTIONS_VERBOSE_DOC = "Deprecated and ignored. Logging is controlled by the worker logging configuration.";
  public final List<URL> schemaUrls;
  public final String schemaPackage;

  public FromXmlConfig(Map<?, ?> originals) {
    super(config(), originals);
    this.schemaUrls = ConfigUtils.urls(this, SCHEMA_PATH_CONFIG);
    if (this.schemaUrls.isEmpty()) {
      throw new ConfigException(SCHEMA_PATH_CONFIG, getList(SCHEMA_PATH_CONFIG), "At least one XSD URL is required");
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
                .build()
        ).define(
            ConfigKeyBuilder.of(PACKAGE_CONFIG, ConfigDef.Type.STRING)
                .documentation(PACKAGE_DOC)
                .importance(ConfigDef.Importance.HIGH)
                .validator(new ConfigDef.NonEmptyString())
                .defaultValue(FromXmlConfig.class.getPackage().getName() + ".model")
                .build()
        ).define(
            ConfigKeyBuilder.of(XJC_OPTIONS_STRICT_CHECK_CONFIG, ConfigDef.Type.BOOLEAN)
                .documentation(XJC_OPTIONS_STRICT_CHECK_DOC)
                .importance(ConfigDef.Importance.LOW)
                .defaultValue(true)
                .build()
        ).define(
            ConfigKeyBuilder.of(XJC_OPTIONS_AUTOMATIC_NAME_CONFLICT_RESOLUTION_ENABLED_CONFIG, ConfigDef.Type.BOOLEAN)
                .documentation(XJC_OPTIONS_AUTOMATIC_NAME_CONFLICT_RESOLUTION_ENABLED_DOC)
                .importance(ConfigDef.Importance.LOW)
                .defaultValue(false)
                .build()
        ).define(
            ConfigKeyBuilder.of(XJC_OPTIONS_VERBOSE_CONFIG, ConfigDef.Type.BOOLEAN)
                .documentation(XJC_OPTIONS_VERBOSE_DOC)
                .importance(ConfigDef.Importance.LOW)
                .defaultValue(false)
                .build()
        );
  }


}
