package rogo.sketchrender.util;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Identifier implements Comparable<Identifier> {
    private static final Map<String, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final String ILLEGAL_CHARACTERS = "[\\s]"; // forbid spaces, you can expand this regex
    private final String name;

    private Identifier(String name) {
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
    public static Identifier of(String name) {
        return CACHE.computeIfAbsent(name, Identifier::new);
    }

    /**
     * Converts any object to string via toString() and wraps it as an Identifier.
     */
    public static Identifier valueOf(Object obj) {
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
        return obj instanceof Identifier && ((Identifier) obj).name.equals(this.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public int compareTo(Identifier other) {
        return this.name.compareTo(other.name);
    }

    // Optional: expose the cache size (e.g. for debugging)
    public static int getCacheSize() {
        return CACHE.size();
    }

    /**
     * GSON serializer/deserializer
     */
    public static class GsonAdapter implements JsonSerializer<Identifier>, JsonDeserializer<Identifier> {
        @Override
        public JsonElement serialize(Identifier src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.name);
        }

        @Override
        public Identifier deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("Expected Identifier as string");
            }
            return Identifier.of(json.getAsString());
        }
    }
}