package xmlbeans.transformer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class DirectoryClassLoader extends ClassLoader {
    private final File directory;

    public DirectoryClassLoader(String directoryPath) {
        this.directory = new File(directoryPath);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] classData = loadClassData(name);
        if (classData == null) {
            throw new ClassNotFoundException("Class " + name + " not found in " + directory.getAbsolutePath());
        }
        return defineClass(name, classData, 0, classData.length);
    }

    private byte[] loadClassData(String className) {
        String classPath = className.replace('.', File.separatorChar) + ".class";
        File classFile = new File(directory, classPath);

        if (!classFile.exists()) {
            return null;
        }

        try (FileInputStream inputStream = new FileInputStream(classFile)) {
            byte[] buffer = new byte[(int) classFile.length()];
            int bytesRead = inputStream.read(buffer);
            if (bytesRead != buffer.length) {
                throw new IOException("Failed to read the entire class file: " + classFile.getAbsolutePath());
            }
            return buffer;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected URL findResource(String name) {
        File resourceFile = new File(directory, name);
        if (resourceFile.exists()) {
            try {
                return resourceFile.toURI().toURL(); // Convert the file path to a URL
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
        return null; // Return null if the resource is not found
    }

    public static void main(String[] args) {
        try {
            DirectoryClassLoader loader = new DirectoryClassLoader("/tmp/xmlbeans4419946667062713381/classes");
            URL resource = loader.findResource("/org/apache/xmlbeans/metadata/system/sF8A49D734B95570E31F9C023253684D4/index.xsb");
            Class<?> clazz = loader.loadClass("com.example.MyClass");
            Object instance = clazz.newInstance();
            System.out.println("Class loaded: " + clazz.getName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}

