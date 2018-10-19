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

import java.net.URL;
import java.util.List;
import java.util.Map;

class FromXmlConfig extends AbstractConfig {

  public static final String SCHEMA_PATH_CONFIG = "schema.path";
  static final String SCHEMA_PATH_DOC = "Urls to the schemas to load. http and https paths are supported";

  public static final String PACKAGE_CONFIG = "package";
  static final String PACKAGE_DOC = "The java package xjc will use to generate the source code in. This name will be applied to the resulting schema";

  public final List<URL> schemaUrls;
  public final String xjcPackage;

  public FromXmlConfig(Map<?, ?> originals) {
    super(config(), originals);
    this.schemaUrls = ConfigUtils.urls(this, SCHEMA_PATH_CONFIG);
    this.xjcPackage = getString(PACKAGE_CONFIG);
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
                .defaultValue(FromXmlConfig.class.getPackage().getName() + ".model")
                .build()
        );
  }


}
