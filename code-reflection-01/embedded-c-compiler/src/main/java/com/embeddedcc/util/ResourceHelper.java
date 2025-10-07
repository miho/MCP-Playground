package com.embeddedcc.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ResourceHelper {

    private ResourceHelper() {
    }

    public static String readText(String resourcePath) throws IOException {
        try (InputStream stream = ResourceHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static void copyToPath(String resourcePath, Path destination) throws IOException {
        try (InputStream stream = ResourceHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            Files.createDirectories(destination.getParent());
            Files.write(destination, stream.readAllBytes());
        }
    }
}
