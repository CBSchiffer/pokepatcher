package com.clokkworkk.pokepatcher;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class DatapackManager {

    private static final Path DATAPACK_PATH = Paths.get("run","config", "pokepatcher", "datapacks");
    private static final Path OUTPUT_PATH = Paths.get("run", "config", "pokepatcher", "generated");

    public static void packageDatapacks() {
        try {
            Files.createDirectory(OUTPUT_PATH);
            Files.list(DATAPACK_PATH)
                    .filter(Files::isDirectory)
                    .forEach(DatapackManager::packageDatapack);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void packageDatapack(Path datapackPath) {
        String modId = datapackPath.getFileName().toString().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        Path outputJar = OUTPUT_PATH.resolve(modId + ".jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(outputJar))) {
            jos.putNextEntry(new JarEntry("fabric.mod.json"));
            jos.write(generateFabricModJson(modId).getBytes());
            jos.closeEntry();

            copyDirectoryToJar(datapackPath.resolve("data"), "data/", jos);
            copyDirectoryToJar(datapackPath.resolve("assets"), "assets/", jos);

            PokePatcher.LOGGER.info("Packaged datapack: " + datapackPath.getFileName());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void copyDirectoryToJar(Path sourceDir, String jarPathPrefix, JarOutputStream jos) {
    }

    private static String generateFabricModJson(String modId) {
    }
}
