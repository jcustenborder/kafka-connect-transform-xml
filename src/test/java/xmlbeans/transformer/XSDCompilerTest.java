package xmlbeans.transformer;

import junit.framework.TestCase;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.impl.tool.Parameters;
import org.apache.xmlbeans.impl.tool.SchemaCompiler;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static xmlbeans.transformer.Util.urlToFile;

public class XSDCompilerTest extends TestCase {
    static final String schemaUrl = "https://sftpgo-dev.s3.eu-central-1.amazonaws.com/pivotal_pos-v2.xsd";

    public void testSchemaCompiler() throws IOException, XmlException {
        File schemaFile = urlToFile(schemaUrl);
        SchemaDocument schema = SchemaDocument.Factory.parse(schemaFile);

        Parameters params = new Parameters();
        params.setXsdFiles(schemaFile);
        params.setSrcDir(new File("/home/sjatep/Downloads/pivotal/xmlbeans/src"));
        params.setClassesDir(new File("/home/sjatep/Downloads/pivotal/xmlbeans/classes"));
        assertTrue(SchemaCompiler.compile(params));
    }

    public void testTransformer() throws IOException {
        Map<String, String> settings = new HashMap<>();
        settings.put(FromXmlConfig.SCHEMA_PATH_CONFIG, schemaUrl);

        //final byte[] input = Files.readAllBytes(new File("/home/sjatep/Downloads/pivotal/PointOfService_ep_de_20240829_011039.xml").toPath());
        //final byte[] input = FileUtils.readFileToByteArray(new File("/home/sjatep/Downloads/pivotal/PointOfService_ep_de_20240829_011039.xml"));

        String input = FileUtils.readFileToString(new File("/home/sjatep/Downloads/pivotal/PointOfService_ep_de_20240829_011039.xml"));
        ConnectRecord<?> record = new SinkRecord("filecenter-pivotal", 0,
                null, null,
                Schema.STRING_SCHEMA, input,
                new Date().getTime());
        try (FromXml subject = new FromXml.Value<>()) {
            subject.configure(settings);
            ConnectRecord<?> transformed = subject.apply(record);
            System.out.println(transformed.value());
        }
    }

    public void testUrlToFile() throws IOException {
        File file = urlToFile(schemaUrl);
        System.out.println(file.getAbsolutePath());
        assertTrue(file.isFile());
        assertTrue(file.exists());
        assertTrue(file.length() > 0);
    }
}
