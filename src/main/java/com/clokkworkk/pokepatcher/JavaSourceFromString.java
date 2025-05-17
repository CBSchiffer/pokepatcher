package com.clokkworkk.pokepatcher;

import javax.tools.SimpleJavaFileObject;
import java.net.URI;

/**
 * A simple Java file object that represents a source file in memory.
 * This class is used to create a Java file object from a string of code.
 */
public class JavaSourceFromString extends SimpleJavaFileObject {
    final String code;

    /**
     * Constructs a new JavaSourceFromString object.
     *
     * @param name The name of the Java file (e.g., "MyClass.java").
     * @param code The source code as a string.
     */
    JavaSourceFromString(String name, String code) {
        super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
        this.code = code;
    }

    /**
     * Returns the source code as a CharSequence.
     *
     * @param ignoreEncodingErrors Whether to ignore encoding errors.
     * @return The source code as a CharSequence.
     */
    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return code;
    }
}
