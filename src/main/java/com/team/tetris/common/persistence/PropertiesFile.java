package com.team.tetris.common.persistence;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** UTF-8 storage with replacement only after a complete temporary file is written. */
public final class PropertiesFile {
    private PropertiesFile() { }

    public static Properties read(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (NoSuchFileException missing) {
            return properties;
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Malformed properties file: " + path, malformed);
        }
        return properties;
    }

    public static void write(Path path, Properties properties) throws IOException {
        Path target = path.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".tetris-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "Tetris data - UTF-8");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) throw new IllegalArgumentException("Missing property: " + key);
        return value;
    }

    public static void requireVersion(Properties properties) {
        if (!"1".equals(required(properties, "version"))) {
            throw new IllegalArgumentException("Unsupported data version");
        }
    }
}
