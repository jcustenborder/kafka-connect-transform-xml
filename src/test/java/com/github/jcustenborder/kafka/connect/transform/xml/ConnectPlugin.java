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

import com.google.common.base.Strings;
import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JAnnotationValue;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JFormatter;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.model.Model;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.connect.data.Date;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.data.Time;
import org.apache.kafka.connect.data.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import javax.xml.bind.annotation.XmlSchemaType;
import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConnectPlugin extends Plugin {
  private static final Logger log = LoggerFactory.getLogger(ConnectPlugin.class);

  @Override
  public String getOptionName() {
    return "connect";
  }

  @Override
  public int parseArgument(Options opt, String[] args, int i)
      throws BadCommandLineException, IOException {
    return 1;
  }

  @Override
  public String getUsage() {
    return "";
  }

  @Override
  public void onActivated(Options opts) throws BadCommandLineException {
    super.onActivated(opts);
    log.info("onActivated");

  }

  @Override
  public void postProcessModel(Model model, ErrorHandler errorHandler) {
    super.postProcessModel(model, errorHandler);
    log.info("postProcessModel");

  }

  final static JType VOID_TYPE = new JCodeModel().VOID;

  Map<String, Object> attributes(JFieldVar field, Class<?> annotationClass) {
    for (JAnnotationUse annotationUse : field.annotations()) {
      log.trace("isRequired() - name = '{}' getAnnotationClass = '{}'", field.name(), annotationUse.getAnnotationClass().fullName());
      if (annotationUse.getAnnotationClass().fullName().equals(annotationClass.getName())) {
        Map<String, Object> result = new LinkedHashMap<>();



        return result;
      }
    }
    return null;
  }

  String attributeValue(JFieldVar field, Class<?> annotationClass, String param) {
    for (JAnnotationUse annotationUse : field.annotations()) {
      log.trace("isRequired() - name = '{}' getAnnotationClass = '{}'", field.name(), annotationUse.getAnnotationClass().fullName());
      if (annotationUse.getAnnotationClass().fullName().equals(annotationClass.getName())) {
        StringWriter writer = new StringWriter();
        JFormatter formatter = new JFormatter(writer);
        ((JAnnotationValue) annotationUse.getAnnotationMembers().get(param)).generate(formatter);
        return StringUtils.strip(writer.toString(), "\"");
      }
    }
    return null;
  }

  boolean isRequired(JFieldVar fieldVar) {
    for (JAnnotationUse annotationUse : fieldVar.annotations()) {
      log.trace("isRequired() - name = '{}' getAnnotationClass = '{}'", fieldVar.name(), annotationUse.getAnnotationClass().fullName());
      if (annotationUse.getAnnotationClass().fullName().equals("javax.xml.bind.annotation.XmlElement")) {
        StringWriter writer = new StringWriter();
        JFormatter formatter = new JFormatter(writer);
        ((JAnnotationValue) annotationUse.getAnnotationMembers().get("required")).generate(formatter);
        return Boolean.parseBoolean(writer.toString());
      }
    }
    return false;
  }

  JFieldVar processSchema(JCodeModel codeModel, ClassOutline classOutline) {
    final Map<String, JFieldVar> fields = classOutline.implClass.fields();
    final JClass schemaBuilderJClass = codeModel.ref(SchemaBuilder.class);
    final JFieldVar schemaVariable = classOutline.implClass.field(JMod.PUBLIC | JMod.STATIC | JMod.FINAL, Schema.class, "CONNECT_SCHEMA");
    final JMethod staticConstructor = classOutline.implClass.constructor(JMod.STATIC);
    final JBlock constructorBlock = staticConstructor.body();
    final JVar builderVar = constructorBlock.decl(schemaBuilderJClass, "builder", schemaBuilderJClass.staticInvoke("struct"));
    final String schemaName = String.format("%s.%s", classOutline._package()._package().name(), classOutline.implClass.name());
    constructorBlock.invoke(builderVar, "name").arg(schemaName);
    constructorBlock.invoke(builderVar, "optional");

    final JVar fieldBuilderVar = constructorBlock.decl(schemaBuilderJClass, "fieldBuilder");


    for (final Map.Entry<String, JFieldVar> field : fields.entrySet()) {
      log.trace("processSchema() - processing name = '{}' type = '{}'", field.getKey(), field.getValue().type().name());
      if (schemaVariable.name().equals(field.getKey())) {
        log.trace("processSchema() - skipping '{}' cause we added it.", field.getKey());
        continue;
      }


      final String attributeValue = attributeValue(field.getValue(), XmlSchemaType.class, "name");


      if (!Strings.isNullOrEmpty(attributeValue)) {
        switch (attributeValue) {
          case "date":
            JClass connectDateJClass = codeModel.ref(Date.class);
            constructorBlock.assign(fieldBuilderVar, connectDateJClass.staticInvoke("builder"));
            break;
          case "time":
            JClass connectTimeJClass = codeModel.ref(Time.class);
            constructorBlock.assign(fieldBuilderVar, connectTimeJClass.staticInvoke("builder"));
            break;
          case "dateTime":
            JClass connectTimestampJClass = codeModel.ref(Timestamp.class);
            constructorBlock.assign(fieldBuilderVar, connectTimestampJClass.staticInvoke("builder"));
            break;
          case "positiveInteger":
            constructorBlock.assign(fieldBuilderVar, schemaBuilderJClass.staticInvoke("int64"));
            break;
          default:
            throw new IllegalStateException(
                String.format("Unknown type %s", attributeValue)
            );
        }
      } else {
        constructorBlock.assign(fieldBuilderVar, schemaBuilderJClass.staticInvoke("string"));
      }


      boolean required = isRequired(field.getValue());

      if (!required) {
        constructorBlock.invoke(fieldBuilderVar, "optional");
      }

      constructorBlock.invoke(builderVar, "field").arg(field.getKey()).arg(fieldBuilderVar.invoke("build"));
    }


    //Build the schema
    constructorBlock.assign(schemaVariable, builderVar.invoke("build"));
    return schemaVariable;
  }

  void processToStruct(JFieldVar schemaField, JCodeModel codeModel, ClassOutline classOutline) {
    final Map<String, JFieldVar> fields = classOutline.implClass.fields();
    final JClass structClass = codeModel.ref(Struct.class);
    final JMethod method = classOutline.implClass.method(JMod.PUBLIC, structClass, "toStruct");
    final JBlock methodBody = method.body();
    final JVar structVar = methodBody.decl(structClass, "struct", JExpr._new(structClass).arg(schemaField));

    for (final Map.Entry<String, JFieldVar> field : fields.entrySet()) {
      log.trace("processSchema() - processing name = '{}' type = '{}'", field.getKey(), field.getValue().type().name());
      if (schemaField.name().equals(field.getKey())) {
        log.trace("processSchema() - skipping '{}' cause we added it.", field.getKey());
        continue;
      }

      methodBody.invoke(structVar, "put")
          .arg(field.getKey())
          .arg(JExpr.ref(JExpr._this(), field.getKey()));
    }

    methodBody._return(structVar);
  }

  void processFromStruct(JCodeModel codeModel, ClassOutline classOutline) {

  }

  @Override
  public boolean run(Outline model, Options options, ErrorHandler errorHandler) throws SAXException {
    JCodeModel codeModel = model.getCodeModel();
    for (ClassOutline classOutline : model.getClasses()) {
      log.trace("run - {}", classOutline.implClass.name());
      JFieldVar schemaField = processSchema(codeModel, classOutline);
      processToStruct(schemaField, codeModel, classOutline);
      processFromStruct(codeModel, classOutline);
    }

    return true;
  }
}
