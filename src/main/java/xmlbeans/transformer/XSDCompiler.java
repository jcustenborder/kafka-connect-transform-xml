/**
 * Copyright © 2017 Jeremy Custenborder (jcustenborder@gmail.com)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xmlbeans.transformer;

import org.apache.kafka.connect.errors.DataException;
import org.apache.xmlbeans.SchemaTypeLoader;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.impl.tool.Parameters;
import org.apache.xmlbeans.impl.tool.SchemaCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;

import static org.apache.commons.io.FileUtils.deleteDirectory;
import static xmlbeans.transformer.Util.urlToFile;

public class XSDCompiler implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(FromXml.class);

    final File genClassesDirectory;
    final File genSourcesDirectory;
    final ClassLoader classLoader;
    final FromXmlConfig config;
    private SchemaTypeLoader stl;

    public XSDCompiler(FromXmlConfig config) throws IOException {
        this.config = config;
        File genFilesDirectory = Files.createTempDirectory("xmlbeans").toFile();
        this.genClassesDirectory = new File(genFilesDirectory, "classes");
        this.genSourcesDirectory = new File(genFilesDirectory, "src");
        this.classLoader = new DirectoryClassLoader(genClassesDirectory.getAbsolutePath());
    }

    public void createCompileContext() {
        Parameters params = new Parameters();
        File schemaFile;
        try {
            schemaFile = urlToFile(config.schemaUrl);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        params.setXsdFiles(schemaFile);
        params.setSrcDir(genSourcesDirectory);
        params.setClassesDir(genClassesDirectory);
        boolean compile = SchemaCompiler.compile(params);
        if (!compile) {
            throw new DataException("Schema compilation failed");
        }

        try {
            this.stl = XmlBeans.typeLoaderForClassLoader(classLoader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public XmlObject unmarshal(Reader input) throws Exception {
        final XmlOptions options = new XmlOptions();
        options.setCompileNoUpaRule();
        options.setCompileNoValidation();
        options.setCompileDownloadUrls();

        return stl.parse(input, null, options);
    }

    @Override
    public void close() {
        try {
            deleteDirectory(genClassesDirectory);
        } catch (IOException e) {
            log.warn("Failed to delete " + genClassesDirectory, e);
        }
        try {
            deleteDirectory(genSourcesDirectory);
        } catch (IOException e) {
            log.warn("Failed to delete " + genSourcesDirectory, e);
        }
    }
}
