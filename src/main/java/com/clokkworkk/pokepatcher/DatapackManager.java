package com.clokkworkk.pokepatcher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.ai.brain.Memory;

import javax.tools.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

public class DatapackManager {

    private static final Path DATAPACK_PATH = Paths.get("config", "pokepatcher", "datapacks");
    private static final Path OUTPUT_PATH = Paths.get( "config", "pokepatcher", "generated");
    private static final Path TEMP_PATH = Paths.get("config", "pokepatcher", "temp");

    /**
     * Scans the datapack directory for datapacks and packages them into jar files.
     */
    public static void packageDatapacks() {
        try {
            PokePatcher.LOGGER.info("Scanning for datapacks...");
            Files.createDirectories(DATAPACK_PATH);
            Files.createDirectories(OUTPUT_PATH);
            Files.createDirectories(TEMP_PATH);
            List<Path> roots = new ArrayList<>();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(DATAPACK_PATH)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        roots.add(entry);
                    } else if (entry.toString().endsWith(".zip")) {
                        Path targetDir = TEMP_PATH.resolve(entry.getFileName().toString().replace(".zip", ""));
                        unzip(entry, targetDir);
                        roots.add(targetDir);
                    }
                }
            } catch (IOException e) {
                PokePatcher.LOGGER.error("Failed to read datapack directory: " + DATAPACK_PATH);
                e.printStackTrace();
            }

            roots.stream()
                    .filter(Files::isDirectory)
                    .forEach(DatapackManager::packageDatapack);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Packages the given datapack directory into a jar file.
     * @param datapackPath The path to the datapack directory
     */
    private static void packageDatapack(Path datapackPath) {
        String modId = datapackPath.getFileName().toString().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        String description = "Auto-generated datapack for " + modId;
        Path meta = datapackPath.resolve("pack.mcmeta");

        if (Files.exists(meta)) {
            try (BufferedReader reader = Files.newBufferedReader(meta)) {
                JsonObject json = new Gson().fromJson(reader, JsonObject.class);
                if (json.has("pack")) {
                    JsonObject pack = json.getAsJsonObject("pack");
                    if (pack.has("description")) {
                        description = escapeJson(pack.get("description").getAsString());
                    }
                }
            } catch (Exception e) {
                PokePatcher.LOGGER.error("Failed to parse pack.mcmeta for " + datapackPath);
                e.printStackTrace();
            }
        } else {
            PokePatcher.LOGGER.warn("No pack.mcmeta found for " + datapackPath.getFileName() + ". Skipping!");
            return;
        }


        Path outputJar = OUTPUT_PATH.resolve(modId + ".jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(outputJar))) {
            String packageName = "com.generated." + modId;
            String className = "Main";
            jos.putNextEntry(new JarEntry("fabric.mod.json"));
            String displayName = Arrays.stream(modId.split("_"))
                    .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
                    .collect(Collectors.joining(" ")) + " (Patched)";
            jos.write(generateFabricModJson(modId, displayName, description, packageName + "." + className).getBytes());
            jos.closeEntry();
            String javaSource = generateEntrypointJava(packageName, className, modId);
            byte[] classData = compileJavaClass(packageName, className, javaSource);

            if(classData != null) {
                jos.putNextEntry(new JarEntry(packageName.replace('.', '/') + "/" + className + ".class"));
                jos.write(classData);
                jos.closeEntry();
            }
            copyDirectoryToJar(datapackPath.resolve("data"), "data/", jos);
            copyDirectoryToJar(datapackPath.resolve("assets"), "assets/", jos);
            Path icon = datapackPath.resolve("pack.png");
            if (Files.exists(icon)) {
                jos.putNextEntry(new JarEntry("assets/" + modId + "/icon.png"));
                Files.copy(icon, jos);
                jos.closeEntry();
            }

            PokePatcher.LOGGER.info("Packaged datapack: " + datapackPath.getFileName());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Copies the directory of the given path to the jar output stream.
     * @param sourceDir The source directory to copy
     * @param jarPathPrefix The path prefix to use in the jar
     * @param jos The JarOutputStream to write to
     * @throws IOException when the copy fails
     */
    private static void copyDirectoryToJar(Path sourceDir, String jarPathPrefix, JarOutputStream jos) throws IOException {
        if(!Files.exists(sourceDir)) {
            return;
        }
        Files.walk(sourceDir).forEach(path -> {
            if (Files.isDirectory(path)) return;
            try {
                String jarEntryName = jarPathPrefix + sourceDir.relativize(path).toString().replace("\\", "/");
                jos.putNextEntry(new JarEntry(jarEntryName));
                Files.copy(path, jos);
                jos.closeEntry();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Generates the fabric.mod.json file for the mod with the given modId, name, and description.
     * @param modId The mod namespace from the datapack
     * @param name The name for the mod to be displayed in the mod menu
     * @param description The description for the mod, borrowed from the pack.mcmeta
     * @return The fabric.mod.json file as a string
     */
    private static String generateFabricModJson(String modId, String name, String description, String entrypointClass) {
        return """
                {
                	"schemaVersion": 1,
                	"id": "%s",
                	"version": "1.0.0",
                	"name": "%s",
                	"description": "%s",
                	"authors": [
                		"Auto-Generated by PokePatcher!",
                		"If they put it there, the original author should be in the description!"
                	],
                	"license": "MIT",
                	"icon": "assets/%s/icon.png",
                	"environment": "*",
                	"entrypoints": {
                 		"main": [
                 			"%s"
                 		]
                 	},
                	"depends": {
                		"fabricloader": ">=0.16.14",
                		"minecraft": "~1.21.1",
                		"java": ">=21",
                  		"fabric-api": "*"
                	}
                }
                """.formatted(modId, name, description, modId, entrypointClass);
    }

    /**
     * Generates the entrypoint Java class for the mod
     * @param packageName The package name for the class
     * @param className The name of the class
     * @param modId The mod namespace from the datapack
     * @return The entrypoint Java class as a string
     */
    private static String generateEntrypointJava(String packageName, String className, String modId) {
        return """
                package %s;
                
                import net.fabricmc.api.ModInitializer;
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                
                public class %s implements ModInitializer {
                	public static final Logger LOGGER = LoggerFactory.getLogger("%s");
                
                	@Override
                	public void onInitialize() {
                		LOGGER.info("Loaded datapack mod: %s");
                	}
                }
                """.formatted(packageName, className, modId, modId);
    }

    /**
     * Escapes the given string for use in JSON.
     * @param str The string to escape
     * @return The escaped string
     */
    private static String escapeJson(String str) {
        return str.replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    /**
     * Unzips the given zip file to the target directory.
     * @param zipFile The zip file to unzip
     * @param targetDir The target directory to unzip to
     */
    private static void unzip(Path zipFile, Path targetDir) {
        if(!Files.exists(targetDir)) {
            try {
                Files.createDirectories(targetDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try (FileSystem fs = FileSystems.newFileSystem(zipFile, (ClassLoader) null)) {
            for (Path root : fs.getRootDirectories()) {
                Files.walk(root).forEach(source -> {
                    try {
                        Path dest = targetDir.resolve(root.relativize(source).toString());
                        if (Files.isDirectory(source)) {
                            Files.createDirectories(dest);
                        } else {
                            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Compiles the given Java source code into a byte array.
     * @param packageName The package name for the class
     * @param className The name of the class
     * @param javaSource The Java source code to compile
     * @return The compiled class data as a byte array
     */
    private static byte[] compileJavaClass(String packageName, String className, String javaSource) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            PokePatcher.LOGGER.error("No Java Compiler available. Make sure you are using a JDK, not a JRE.");
            throw new IllegalStateException("No Java compiler available");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        // Instantiate a JavaFileManager to store the compiled output in memory
        StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(diagnostics, null, null);
        MemoryJavaFileManager memoryJavaFileManager = new MemoryJavaFileManager(standardFileManager);

        String fullClassName = packageName + "." + className;
        JavaFileObject sourceObject = new JavaSourceFromString(fullClassName, javaSource);

        JavaCompiler.CompilationTask task = compiler.getTask(
                null, memoryJavaFileManager, diagnostics, null, null, Collections.singletonList(sourceObject)
        );

        boolean success = task.call();
        if (!success) {
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                PokePatcher.LOGGER.error("Error on line {}: {}", diagnostic.getLineNumber(), diagnostic.getMessage(null));
            }
            throw new RuntimeException("Compilation failed");
        }
        return memoryJavaFileManager.getCompiledClassData(fullClassName);
    }
}
