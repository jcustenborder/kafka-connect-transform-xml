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

import com.github.jcustenborder.kafka.connect.xml.Connectable;
import com.github.jcustenborder.kafka.connect.xml.KafkaConnectPlugin;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.sun.codemodel.JCodeModel;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.api.S2JJAXBModel;
import com.sun.tools.xjc.api.SchemaCompiler;
import com.sun.tools.xjc.api.XJC;
import org.apache.kafka.connect.errors.ConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class XSDCompiler implements Closeable {
  private static final Logger log = LoggerFactory.getLogger(XSDCompiler.class);
  final List<SchemaState> schemas;
  final File tempDirectory;
  final URLClassLoader classLoader;

  public XSDCompiler(List<SchemaState> schemas) {
    this.schemas = schemas;
    this.tempDirectory = Files.createTempDir();
    try {
      this.classLoader = new URLClassLoader(
          new URL[]{
              tempDirectory.toURL()
          },
          Connectable.class.getClassLoader()
      );
    } catch (MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }


  public JAXBContext compileContext() throws IOException {

    List<String> objectFactoryClasses = new ArrayList<>();
    objectFactoryClasses.add(Connectable.class.getName());
    Set<String> packages = new LinkedHashSet<>();
    for (SchemaState state : this.schemas) {
      log.info("compileContext() - Generating source for {}", state.url);
      objectFactoryClasses.add(state.objectFactoryClass());
      packages.add(state.packageName());
      SchemaCompiler schemaCompiler = XJC.createSchemaCompiler();
      Options options = schemaCompiler.getOptions();
      options.activePlugins.add(new KafkaConnectPlugin());
      options.strictCheck = false;

      try (InputStream inputStream = new ByteArrayInputStream(state.content)) {
        InputSource inputSource = new InputSource();
        inputSource.setByteStream(inputStream);
        inputSource.setSystemId(state.url.toString());
        schemaCompiler.parseSchema(inputSource);
      }
      schemaCompiler.setDefaultPackageName(state.packageName());
      schemaCompiler.setErrorListener(new ConnectErrorListener(log));
      S2JJAXBModel model = schemaCompiler.bind();

      if (null == model) {
        throw new ConnectException("Schema compiler could not bind schema.");
      }

      JCodeModel jCodeModel = model.generateCode(null, new ConnectErrorListener(log));

      log.trace("compileContext() - Building model to {}", tempDirectory);
      jCodeModel.build(tempDirectory);
    }

    List<File> sourceFiles =
        StreamSupport.stream(
            Files.fileTreeTraverser().preOrderTraversal(tempDirectory).spliterator(),
            false
        )
            .filter(File::isFile)
            .collect(Collectors.toList());

    if (log.isTraceEnabled()) {
      log.trace("compileContext() - found {} file(s).\n{}",
          sourceFiles.size(),
          Joiner.on('\n').join(sourceFiles)
      );
    }

    final String classPath = System.getProperty("java.class.path");
    List<String> classPathList = new ArrayList<>();
    classPathList.addAll(ImmutableList.copyOf(classPath.split(":")));
    final URL connectableUrl = Connectable.class.getProtectionDomain().getCodeSource().getLocation();
    classPathList.add(connectableUrl.toString());

    List<String> optionList = new ArrayList<>();
    optionList.add("-classpath");
    optionList.add(Joiner.on(':').join(classPathList));

    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
    Locale locale = Locale.getDefault();

    try (StandardJavaFileManager fileManager = javaCompiler.getStandardFileManager(diagnostics, locale, null)) {
      Iterable<? extends JavaFileObject> compilationUnit = fileManager.getJavaFileObjectsFromFiles(sourceFiles);
      JavaCompiler.CompilationTask compilerTask = javaCompiler.getTask(
          null,
          fileManager,
          diagnostics,
          optionList,
          null,
          compilationUnit);

      log.info("Compiling...");
      if (!compilerTask.call()) {
        log.error("Exception while compiling source.");
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
          log.error(
              "Error on line {} in {}\n{}",
              diagnostic.getLineNumber(),
              diagnostic.getSource().toUri(),
              diagnostic.getMessage(locale)
          );
        }
      }
    }


    List<Class<?>> objectFactories = new ArrayList<>();

    for (String s : objectFactoryClasses) {
      try {
        log.info("Loading {}", s);

        objectFactories.add(
            classLoader.loadClass(s)
        );
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException(
            "Exception thrown while loading " + s,
            e
        );
      }
    }
    log.info("Creating JAXBContext");

    try {
      return JAXBContext.newInstance(Joiner.on(':').join(packages), classLoader);
    } catch (JAXBException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void close() throws IOException {
//    log.trace("close() - Cleaning up temp directory '{}'", this.tempDirectory);
//    java.nio.file.Files.walkFileTree(this.tempDirectory.toPath(), new SimpleFileVisitor<Path>() {
//      @Override
//      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
//        log.trace("close() - Deleting {}", file);
//        java.nio.file.Files.delete(file);
//        return FileVisitResult.CONTINUE;
//      }
//
//      @Override
//      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
//        log.trace("close() - Deleting {}", dir);
//        java.nio.file.Files.delete(dir);
//        return FileVisitResult.CONTINUE;
//      }
//    });
  }

  static class SchemaState {
    final URL url;
    final byte[] content;
    final String packageName;

    SchemaState(URL url, byte[] content, String packageName) {
      this.url = url;
      this.content = content;
      this.packageName = packageName;
    }

    public static SchemaState of(URL url, byte[] content, String packageName) {
      return new SchemaState(url, content, packageName);
    }

    public String packageName() {
      return this.packageName;
    }

    public String objectFactoryClass() {
      return String.format("%s.ObjectFactory", packageName());
    }
  }


  public static XSDCompiler create(FromXmlConfig config) {
    List<SchemaState> schemas = new ArrayList<>();
    for (URL url : config.schemaUrls) {
      log.info("Loading schema from {}", url);
      final byte[] buffer;
      try (InputStream inputStream = url.openStream()) {
        buffer = ByteStreams.toByteArray(inputStream);
      } catch (IOException e) {
        throw new IllegalStateException(
            String.format("Exception thrown while loading schema. Url='{}'", url),
            e
        );
      }
      schemas.add(SchemaState.of(url, buffer, config.xjcPackage));
    }

    return new XSDCompiler(ImmutableList.copyOf(schemas));
  }
}
