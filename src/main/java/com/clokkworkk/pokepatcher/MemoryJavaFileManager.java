package com.clokkworkk.pokepatcher;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * A custom JavaFileManager that stores compiled class data in memory.
 * This is useful for dynamically compiling Java classes and retrieving their bytecode.
 */
public class MemoryJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
    private final Map<String, ByteArrayOutputStream> compiledClassData = new HashMap<>();

    /**
     * Constructs a MemoryJavaFileManager with the specified standard file manager.
     *
     * @param fileManager The standard Java file manager to delegate to.
     */
    protected MemoryJavaFileManager(StandardJavaFileManager fileManager) {
        super(fileManager);
    }

    /**
     * Constructs a MemoryJavaFileManager with the specified standard file manager.
     *
     * @param location a package-oriented location
     * @param className the name of a class
     * @param kind the kind of file, must be one of {@link
     * JavaFileObject.Kind#SOURCE SOURCE} or {@link
     * JavaFileObject.Kind#CLASS CLASS}
     * @param sibling a file object to be used as hint for placement;
     * might be {@code null}
     * @return a new JavaFileObject for the specified class name
     */
    @Override
    public JavaFileObject getJavaFileForOutput(
            Location location,
            String className,
            JavaFileObject.Kind kind,
            FileObject sibling) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        compiledClassData.put(className, outputStream);
        return new SimpleJavaFileObject(URI.create("byte:///" + className.replace('.', '/') + kind.extension), kind) {
            @Override
            public OutputStream openOutputStream() {
                return outputStream;
            }
        };
    }

    /**
     * Retrieves the compiled class data for the specified class name.
     *
     * @param className The name of the class whose compiled data is to be retrieved.
     * @return The byte array containing the compiled class data, or null if not found.
     */
    public byte[] getCompiledClassData(String className) {
        ByteArrayOutputStream outputStream = compiledClassData.get(className);
        return outputStream != null ? outputStream.toByteArray() : null;
    }
}
