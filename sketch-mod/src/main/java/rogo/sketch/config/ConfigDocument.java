package rogo.sketch.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class ConfigDocument {
    private final Path path;
    private final Properties properties = new Properties();
    private boolean loaded = false;

    ConfigDocument(Path path) {
        this.path = path;
    }

    String get(String key) {
        ensureLoaded();
        return properties.getProperty(key);
    }

    boolean contains(String key) {
        ensureLoaded();
        return properties.containsKey(key);
    }

    void set(String key, String value) {
        ensureLoaded();
        properties.setProperty(key, value);
    }

    Path path() {
        return path;
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.exists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config file: " + path, e);
        }
    }

    void save(String comment) {
        ensureLoaded();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                properties.store(writer, comment);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save config file: " + path, e);
        }
    }
}
