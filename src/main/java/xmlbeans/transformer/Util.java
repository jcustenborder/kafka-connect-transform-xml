package xmlbeans.transformer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class Util {
    public static File urlToFile(String address) throws IOException {
        URL url = URI.create(address).toURL();
        return urlToFile(url);
    }

    public static File urlToFile(URL url) throws IOException {
        File tempSchemaFile = File.createTempFile("schema", ".xsd");

        ReadableByteChannel rbc = Channels.newChannel(url.openStream());
        try (FileOutputStream fos = new FileOutputStream(tempSchemaFile)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
        return tempSchemaFile;
    }
}
