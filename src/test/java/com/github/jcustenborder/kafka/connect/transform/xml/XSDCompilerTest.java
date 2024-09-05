package com.github.jcustenborder.kafka.connect.transform.xml;

import com.google.common.collect.ImmutableMap;
import com.sun.tools.xjc.api.S2JJAXBModel;
import com.sun.tools.xjc.api.SchemaCompiler;
import com.sun.tools.xjc.api.XJC;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.tools.ant.filters.StringInputStream;
import org.junit.Test;
import org.xml.sax.InputSource;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Map;

import static junit.framework.Assert.fail;

public class XSDCompilerTest {
    static final String schemaUrl = "https://sftpgo-dev.s3.eu-central-1.amazonaws.com/pivotal_pos-v2.xsd";

    @Test
    public void test() throws Exception {

        InputStream is;
        is = Files.newInputStream(Paths.get("/home/sjatep/w/go/src/data-supply/pivotal/docs/pivotal_pos.xsd"));
        is = openUrl("https://sftpgo-dev.s3.eu-central-1.amazonaws.com/pivotal.xsd");
        is = Files.newInputStream(Paths.get("/home/sjatep/Downloads/pivotal.xsd"));
        InputSource inputSource = new InputSource(is);
        inputSource.setSystemId("123");

        SchemaCompiler schemaCompiler = XJC.createSchemaCompiler();
        schemaCompiler.parseSchema(inputSource);
        S2JJAXBModel model = schemaCompiler.bind();

        if (null == model) {
            fail();
        }

    }

    @Test
    public void testXsdCompiler() throws IOException, JAXBException {
        final String schemaUrl = "https://sftpgo-dev.s3.eu-central-1.amazonaws.com/pivotal_pos-v2.xsd";
        File file = new File("/home/sjatep/Downloads/pivotal/PointOfService_ep_de_20240829_011039.xml");
        Map<String, ?> settings = ImmutableMap.of(FromXmlConfig.SCHEMA_PATH_CONFIG, schemaUrl);
        FromXmlConfig cfg = new FromXmlConfig(settings);
        try (XSDCompiler xsdCompiler = new XSDCompiler(cfg)) {
            Unmarshaller u = xsdCompiler.compileContext().createUnmarshaller();
            Object object = u.unmarshal(file);
            System.out.println(object);
        }
    }

    @Test
    public void testTransformer() throws IOException {
        Map<String, ?> settings = ImmutableMap.of(FromXmlConfig.SCHEMA_PATH_CONFIG, schemaUrl);

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
            System.out.printf("");
        }
    }

    private InputStream openUrl(String httpsUrl) throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(httpsUrl);
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    // Get the input stream from the response entity
                    try (InputStream inputStream = response.getEntity().getContent()) {
                        byte[] bytes = IOUtils.toByteArray(inputStream);
                        return new StringInputStream(new String(bytes));
                    }
                } else {
                    System.out.println("Failed to get data. HTTP response code: " + response.getStatusLine().getStatusCode());
                }
            }
        }

        return null;
    }
}
