package xmlbeans.transformer;

import com.github.jcustenborder.kafka.connect.utils.config.Description;
import com.github.jcustenborder.kafka.connect.utils.config.DocumentationTip;
import com.github.jcustenborder.kafka.connect.utils.config.Title;
import com.github.jcustenborder.kafka.connect.utils.data.SchemaHelper;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.transforms.Transformation;
import org.apache.xmlbeans.XmlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Map;

import static org.apache.commons.io.ByteOrderMark.UTF_BOM;

@Title("FromXML")
@Description("This transformation is used to read XML data stored as bytes or a string and convert " +
        "the XML to a structure that is strongly typed in connect. This allows data to be converted from XML " +
        "and stored as AVRO in a topic for example. ")
@DocumentationTip("XML schemas can be much more complex that what can be expressed in a Kafka " +
        "Connect struct. Elements that can be expressed as an anyType or something similar cannot easily " +
        "be used to infer type information.")
public abstract class FromXml<R extends ConnectRecord<R>> implements Transformation<R> {
    private static final Logger log = LoggerFactory.getLogger(FromXml.class);
    private static final ByteOrderMark[] BOMS = {ByteOrderMark.UTF_8, ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_32BE, ByteOrderMark.UTF_32LE};

    FromXmlConfig config;

    protected final boolean isKey;
    private XSDCompiler compiler;

    protected FromXml(boolean isKey) {
        this.isKey = isKey;
    }

    public ConfigDef config() {
        return FromXmlConfig.config();
    }

    public void close() {
    }

    public void configure(Map<String, ?> settings) {

        this.config = new FromXmlConfig(settings);

        try {
            this.compiler = new XSDCompiler(this.config);
            compiler.createCompileContext();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected SchemaAndValue process(Schema schema, Object value) {
        if (null == schema && null == value) {
            return new SchemaAndValue(
                    null,
                    null
            );
        }

        if (null == schema) {
            log.trace("process() - Determining schema");
            schema = SchemaHelper.schema(value);
            return process(schema, value);
        }

        Reader reader = null;
        if (Schema.Type.STRING == schema.type()) {
            String filtered = filterOutUtfBOM((String) value);
            reader = new StringReader(filtered);
        }

        if (Schema.Type.BYTES == schema.type()) {
            try {
                reader = new InputStreamReader(BOMInputStream.builder()
                        .setByteOrderMarks(BOMS)
                        .setInputStream(new ByteArrayInputStream((byte[]) value))
                        .get());
            } catch (IOException e) {
                log.warn("", e);
                return new SchemaAndValue(
                        null,
                        null
                );
            }
        }

        if (reader == null) {
            return new SchemaAndValue(
                    null,
                    null
            );
        }

        try {
            XmlObject xmlObject = compiler.unmarshal(reader);
            return new SchemaAndValue(schema, xmlObject);
        } catch (Exception e) {
            log.warn("", e);
            return new SchemaAndValue(
                    null,
                    null
            );
        }
    }

    public static class Key<R extends ConnectRecord<R>> extends FromXml<R> {
        public Key() {
            super(true);
        }

        @Override
        public R apply(R r) {
            final SchemaAndValue transformed = process(r.keySchema(), r.key());

            return r.newRecord(
                    r.topic(),
                    r.kafkaPartition(),
                    transformed.schema(),
                    transformed.value(),
                    r.valueSchema(),
                    r.value(),
                    r.timestamp()
            );
        }
    }

    public static class Value<R extends ConnectRecord<R>> extends FromXml<R> {
        public Value() {
            super(false);
        }

        @Override
        public R apply(R r) {
            final SchemaAndValue transformed = process(r.valueSchema(), r.value());

            return r.newRecord(
                    r.topic(),
                    r.kafkaPartition(),
                    r.keySchema(),
                    r.key(),
                    transformed.schema(),
                    transformed.value(),
                    r.timestamp()
            );
        }
    }

    private static String filterOutUtfBOM(String s) {
        if (s.startsWith(String.valueOf(UTF_BOM))) {
            s = s.substring(1);
        }
        return s;
    }
}
