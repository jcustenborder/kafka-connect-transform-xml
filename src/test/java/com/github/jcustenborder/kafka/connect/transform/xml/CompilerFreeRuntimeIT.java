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

import org.apache.kafka.connect.data.ConnectSchema;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.transforms.Transformation;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompilerFreeRuntimeIT {
  @TempDir
  Path temporary;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void packagedPluginConvertsWithoutCompilerModulesOrWritableTemp(boolean inline) throws Exception {
    Path bundle = Path.of(System.getProperty("plugin.zip"));
    Path unpacked = temporary.resolve("plugin");
    try (ZipFile zip = new ZipFile(bundle.toFile())) {
      for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
        Path target = unpacked.resolve(entry.getName()).normalize();
        if (!target.startsWith(unpacked)) {
          throw new IOException("Invalid plugin archive entry " + entry.getName());
        }
        if (!entry.isDirectory()) {
          Files.createDirectories(target.getParent());
          try (InputStream input = zip.getInputStream(entry)) {
            Files.copy(input, target);
          }
        }
      }
    }
    String archiveName = bundle.getFileName().toString();
    Path libraries = unpacked.resolve(archiveName.substring(0, archiveName.length() - 4)).resolve("lib");
    Path jar = libraries.resolve(Path.of(System.getProperty("plugin.jar")).getFileName());
    // The worker supplies Kafka/SLF4J; every other runtime dependency must come from the ZIP.
    String classpath = String.join(File.pathSeparator, location(CompilerFreeRuntimeIT.class),
        libraries.resolve("*").toString(), location(Schema.class),
        location(org.apache.kafka.common.config.AbstractConfig.class), location(org.slf4j.LoggerFactory.class));
    Path noTemp = Files.writeString(temporary.resolve("not-a-directory"), "blocked");
    Path output = temporary.resolve("runtime.log");
    Process process = new ProcessBuilder(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "--limit-modules", "java.base,java.xml,java.logging",
        "-Djava.io.tmpdir=" + noTemp, "-cp", classpath, Probe.class.getName(), jar.toString(), Boolean.toString(inline))
        .redirectErrorStream(true).redirectOutput(output.toFile()).start();
    if (!process.waitFor(60, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new AssertionError("Compiler-free conversion timed out: " + Files.readString(output));
    }
    String log = Files.readString(output);
    assertEquals(0, process.exitValue(), log);
    assertTrue(log.contains("Compiler-free STRING/BYTES to Struct conversion passed"), log);
  }

  private static String location(Class<?> type) throws Exception {
    return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
  }

  public static class Probe {
    public static void main(String[] args) throws Exception {
      if (ModuleLayer.boot().findModule("jdk.compiler").isPresent()
          || ModuleLayer.boot().findModule("java.compiler").isPresent()) {
        throw new AssertionError("Compiler modules must not be available");
      }
      Path actualJar = Path.of(FromXml.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      if (!Files.isSameFile(actualJar, Path.of(args[0]))) {
        throw new AssertionError("Expected packaged plugin " + args[0] + " but loaded " + actualJar);
      }
      Set<Class<?>> providers = new HashSet<>();
      for (Transformation<?> provider : ServiceLoader.load(Transformation.class)) {
        providers.add(provider.getClass());
        provider.close();
      }
      if (!providers.containsAll(Set.of(FromXml.Key.class, FromXml.Value.class))) {
        throw new AssertionError("Missing service providers: " + providers);
      }
      Path fixture = Path.of("src/test/resources/com/github/jcustenborder/kafka/connect/transform/xml");
      String xml = Files.readString(fixture.resolve("books.xml"));
      boolean inline = Boolean.parseBoolean(args[1]);
      Map<String, String> settings = inline
          ? Map.of("schema.content", Files.readString(fixture.resolve("books.xsd")))
          : Map.of("schema.path", fixture.resolve("books.xsd").toUri().toString());
      for (boolean key : List.of(false, true)) {
        try (FromXml<SinkRecord> transform = key ? new FromXml.Key<>() : new FromXml.Value<>()) {
          transform.configure(settings);
          for (boolean bytes : List.of(false, true)) {
            Object input = bytes ? xml.getBytes(StandardCharsets.UTF_8) : xml;
            Schema inputSchema = bytes ? Schema.BYTES_SCHEMA : Schema.STRING_SCHEMA;
            SinkRecord result = transform.apply(new SinkRecord("books", 0,
                key ? inputSchema : null, key ? input : null,
                key ? null : inputSchema, key ? null : input, 0L));
            Schema schema = key ? result.keySchema() : result.valueSchema();
            Object value = key ? result.key() : result.value();
            ConnectSchema.validateValue(schema, value);
            List<Struct> books = ((Struct) value).getArray("book");
            if (books.size() != 2 || !"Writer".equals(books.get(0).getString("author"))) {
              throw new AssertionError("Incorrect structured book output");
            }
          }
        }
      }
      System.out.println("Compiler-free STRING/BYTES to Struct conversion passed");
    }
  }
}
