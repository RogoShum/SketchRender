package rogo.sketch.core.util;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class KeyId implements Comparable<KeyId> {
    private static final Map<String, KeyId> CACHE = new ConcurrentHashMap<>();
    private static final String ILLEGAL_CHARACTERS = "[\\s]"; // forbid spaces, you can expand this regex
    private final String name;

    private KeyId(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Identifier name cannot be null or empty.");
        }
        if (name.matches(".*" + ILLEGAL_CHARACTERS + ".*")) {
            throw new IllegalArgumentException("Identifier contains illegal characters: " + name);
        }
        this.name = name;
    }

    /**
     * Creates or retrieves an Identifier from a string (cached).
     */
    public static KeyId of(String name) {
        return CACHE.computeIfAbsent(name, KeyId::new);
    }

    public static KeyId of(String head, String body) {
        return CACHE.computeIfAbsent(head + ":" + body, KeyId::new);
    }

    /**
     * Converts any object to string via toString() and wraps it as an Identifier.
     */
    public static KeyId valueOf(Object obj) {
        return of(String.valueOf(obj));
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof KeyId && ((KeyId) obj).name.equals(this.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public int compareTo(KeyId other) {
        return this.name.compareTo(other.name);
    }

    // Optional: expose the cache size (e.g. for debugging)
    public static int getCacheSize() {
        return CACHE.size();
    }

    /**
     * GSON serializer/deserializer
     */
    public static class GsonAdapter implements JsonSerializer<KeyId>, JsonDeserializer<KeyId> {
        @Override
        public JsonElement serialize(KeyId src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.name);
        }

        @Override
        public KeyId deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("Expected Identifier as string");
            }
            return KeyId.of(json.getAsString());
        }
    }
}