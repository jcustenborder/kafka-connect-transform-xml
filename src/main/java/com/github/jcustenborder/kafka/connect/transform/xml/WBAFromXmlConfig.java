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
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.api.SchemaCompiler;
import com.sun.tools.xjc.api.XJC;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;

import java.net.URL;
import java.util.List;
import java.util.Map;

class WBAFromXmlConfig extends AbstractConfig {

  public static final String SCHEMA_PATH_CONFIG = "schema.path";
  public static final String PACKAGE_CONFIG = "package";
  public static final String XJC_OPTIONS_STRICT_CHECK_CONFIG = "xjc.options.strict.check.enabled";
  public static final String XJC_OPTIONS_AUTOMATIC_NAME_CONFLICT_RESOLUTION_ENABLED_CONFIG = "xjc.options.automatic.name.conflict.resolution.enabled";
  public static final String XJC_OPTIONS_VERBOSE_CONFIG = "xjc.options.verbose.enabled";
  public static final String REROUTE_ON_FAIL_TOPIC_CONFIG = "reroute.on.fail.topic";
  static final String SCHEMA_PATH_DOC = "Urls to the schemas to load. http and https paths are supported";
  static final String PACKAGE_DOC = "The java package xjc will use to generate the source code in. This name will be applied to the resulting schema";
  static final String XJC_OPTIONS_STRICT_CHECK_DOC = "xjc.options.strict.check.enabled";
  static final String XJC_OPTIONS_AUTOMATIC_NAME_CONFLICT_RESOLUTION_ENABLED_DOC = "xjc.options.automatic.name.conflict.resolution.enabled";
  static final String XJC_OPTIONS_VERBOSE_DOC = "xjc.options.verbose.enabled";
  static final String REROUTE_ON_FAIL_TOPIC_DOC = "When this setting is set, the transform will re-route the record to the specified topic on failure.";
  public final List<URL> schemaUrls;
  public final String xjcPackage;
  public final String rerouteTopic;
  public final boolean optionsStrictCheck;
  public final boolean optionsAutomaticNameConflictResolution;

  public WBAFromXmlConfig(Map<?, ?> originals) {
    super(config(), originals);
    this.schemaUrls = ConfigUtils.urls(this, SCHEMA_PATH_CONFIG);
    this.xjcPackage = getString(PACKAGE_CONFIG);
    this.optionsStrictCheck = getBoolean(XJC_OPTIONS_STRICT_CHECK_CONFIG);
    this.rerouteTopic = getString(REROUTE_ON_FAIL_TOPIC_CONFIG);
    this.optionsAutomaticNameConflictResolution = getBoolean(XJC_OPTIONS_AUTOMATIC_NAME_CONFLICT_RESOLUTION_ENABLED_CONFIG);
  }

  public static ConfigDef config() {
    SchemaCompiler schemaCompiler = XJC.createSchemaCompiler();
    Options options = schemaCompiler.getOptions();

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
                .defaultValue(WBAFromXmlConfig.class.getPackage().getName() + ".model")
                .build()
        ).define(
            ConfigKeyBuilder.of(XJC_OPTIONS_STRICT_CHECK_CONFIG, ConfigDef.Type.BOOLEAN)
                .documentation(XJC_OPTIONS_STRICT_CHECK_DOC)
                .importance(ConfigDef.Importance.LOW)
                .defaultValue(options.strictCheck)
                .build()
        ).define(
            ConfigKeyBuilder.of(XJC_OPTIONS_AUTOMATIC_NAME_CONFLICT_RESOLUTION_ENABLED_CONFIG, ConfigDef.Type.BOOLEAN)
                .documentation(XJC_OPTIONS_AUTOMATIC_NAME_CONFLICT_RESOLUTION_ENABLED_DOC)
                .importance(ConfigDef.Importance.LOW)
                .defaultValue(options.automaticNameConflictResolution)
                .build()
        ).define(
            ConfigKeyBuilder.of(XJC_OPTIONS_VERBOSE_CONFIG, ConfigDef.Type.BOOLEAN)
                .documentation(XJC_OPTIONS_VERBOSE_DOC)
                .importance(ConfigDef.Importance.LOW)
                .defaultValue(options.verbose)
                .build()
        ).define(
            ConfigKeyBuilder.of(REROUTE_ON_FAIL_TOPIC_CONFIG, ConfigDef.Type.STRING)
                    .documentation(REROUTE_ON_FAIL_TOPIC_DOC)
                    .importance(ConfigDef.Importance.LOW)
                    .defaultValue("")
                    .build()
        );
  }


}
