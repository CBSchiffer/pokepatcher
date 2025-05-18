package com.clokkworkk.pokepatcher;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.ai.brain.Memory;

import javax.tools.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

public class DatapackManager {

    private static final Path DATAPACK_PATH = Paths.get("config", "pokepatcher", "datapacks");
    private static final Path OUTPUT_PATH = Paths.get( "mods" );
    private static final Path TEMP_PATH = Paths.get("config", "pokepatcher", "temp");
    private static final Path RECORD_PATH = Paths.get("config", "pokepatcher", "patches.json");

    /**
     * Scans the datapack directory for datapacks and packages them into jar files.
     * This method will also unzip any zip files found in the directory.
     * (Skips any datapacks that have already been patched)
     */
    public static void packageDatapacks() {
        try {
            PokePatcher.LOGGER.info("Scanning for datapacks...");
            Files.createDirectories(DATAPACK_PATH);
            Files.createDirectories(TEMP_PATH);
            List<Path> roots = new ArrayList<>();
            Set<String> patches = readPatches();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(DATAPACK_PATH)) {
                for (Path entry : stream) {
                    if (patches.contains(entry.getFileName().toString())) {
                        PokePatcher.LOGGER.info("Skipping already patched datapack: " + entry.getFileName());
                        continue;
                    }
                    if (Files.isDirectory(entry)) {
                        roots.add(entry);
                        patches.add(entry.getFileName().toString());
                    } else if (entry.toString().endsWith(".zip")) {
                        Path targetDir = TEMP_PATH.resolve(entry.getFileName().toString().replace(".zip", ""));
                        unzip(entry, targetDir);
                        roots.add(targetDir);
                        patches.add(targetDir.getFileName().toString() + ".zip");
                    }
                }
            } catch (IOException e) {
                PokePatcher.LOGGER.error("Failed to read datapack directory: " + DATAPACK_PATH);
                e.printStackTrace();
            }

            roots.stream()
                    .filter(Files::isDirectory)
                    .forEach(DatapackManager::packageDatapack);
            savePatches(patches);
            PokePatcher.LOGGER.info("Finished packaging datapacks! Restart Minecraft to load them!");
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
            jos.putNextEntry(new JarEntry("fabric.mod.json"));
            String displayName = Arrays.stream(modId.split("_"))
                    .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
                    .collect(Collectors.joining(" ")) + " (Patched)";
            jos.write(generateFabricModJson(modId, displayName, description).getBytes());
            jos.closeEntry();

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
    private static String generateFabricModJson(String modId, String name, String description) {
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
                	"entrypoints": {},
                	"depends": {
                		"fabricloader": ">=0.16.14",
                		"minecraft": "~1.21.1",
                		"java": ">=21",
                  		"fabric-api": "*"
                	}
                }
                """.formatted(modId, name, description, modId);
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
     * Reads the patches from the patches.json file.
     * @return A set of patches
     */
    private static Set<String> readPatches() {
        try {
            if (Files.exists(RECORD_PATH)) {
                String json = Files.readString(RECORD_PATH);
                JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
                Set<String> patches = new HashSet<>();
                for (var key : jsonArray) {
                    patches.add(key.getAsString());
                }
                return patches;
            } else {
                return new HashSet<>();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return new HashSet<>();
        }
    }

    /**
     * Saves the patches to the patches.json file.
     * @param patches The set of patches to save
     */
    private static void savePatches(Set<String> patches) {
        try {
            JsonArray jsonArray = new JsonArray();
            for (String patch : patches) {
                jsonArray.add(patch);
            }
            Files.writeString(RECORD_PATH, jsonArray.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
