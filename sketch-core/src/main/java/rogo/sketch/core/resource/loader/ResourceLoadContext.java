package rogo.sketch.core.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import rogo.sketch.core.util.KeyId;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;

/**
 * Resource loading context - encapsulates all dependencies needed during loading.
 * Provides lazy-loaded access to stream, string, and JSON data.
 * Replaces the old ResourceData + scattered parameters pattern.
 */
public class ResourceLoadContext {
    private final KeyId resourceId;
    private final InputStream inputStream;
    private final Gson gson;
    private final Function<KeyId, Optional<InputStream>> subResourceProvider;
    
    // Lazy-loaded cached values
    private String cachedString;
    private JsonObject cachedJson;
    private byte[] cachedBytes;
    private boolean streamConsumed = false;
    
    /**
     * Create a new resource load context.
     *
     * @param resourceId          The identifier of the resource being loaded
     * @param inputStream         The input stream containing resource data
     * @param gson                The Gson instance for JSON parsing
     * @param subResourceProvider Provider for loading sub-resources (e.g., GLSL imports)
     */
    public ResourceLoadContext(KeyId resourceId, 
                               InputStream inputStream, 
                               Gson gson,
                               Function<KeyId, Optional<InputStream>> subResourceProvider) {
        this.resourceId = resourceId;
        this.inputStream = inputStream;
        this.gson = gson;
        this.subResourceProvider = subResourceProvider != null ? subResourceProvider : id -> Optional.empty();
    }
    
    /**
     * Get the resource identifier.
     */
    public KeyId getResourceId() {
        return resourceId;
    }
    
    /**
     * Get the raw input stream.
     * Note: This consumes the stream. Use getString() or getBytes() for reusable access.
     * 
     * @return The input stream
     */
    public InputStream getInputStream() {
        if (streamConsumed) {
            throw new IllegalStateException("Input stream has already been consumed. Use getString() or getBytes() instead.");
        }
        streamConsumed = true;
        return inputStream;
    }
    
    /**
     * Get the Gson instance for JSON parsing.
     */
    public Gson getGson() {
        return gson;
    }
    
    /**
     * Get the resource data as a string.
     * The result is cached for subsequent calls.
     *
     * @return The string content
     */
    public String getString() {
        if (cachedString == null) {
            cachedString = readStreamAsString();
        }
        return cachedString;
    }
    
    /**
     * Get the resource data as bytes.
     * The result is cached for subsequent calls.
     *
     * @return The byte array
     */
    public byte[] getBytes() {
        if (cachedBytes == null) {
            cachedBytes = readStreamAsBytes();
        }
        return cachedBytes;
    }
    
    /**
     * Get the resource data as a JSON object.
     * The result is cached for subsequent calls.
     *
     * @return The JsonObject
     */
    public JsonObject getJson() {
        if (cachedJson == null) {
            cachedJson = gson.fromJson(getString(), JsonObject.class);
        }
        return cachedJson;
    }
    
    /**
     * Parse the JSON content into a specific class.
     *
     * @param clazz The target class
     * @param <T>   The type to parse
     * @return The parsed object
     */
    public <T> T parseJson(Class<T> clazz) {
        return gson.fromJson(getString(), clazz);
    }
    
    /**
     * Get a buffered reader for the input stream.
     *
     * @return A BufferedReader
     */
    public BufferedReader getReader() {
        if (cachedString != null) {
            return new BufferedReader(new java.io.StringReader(cachedString));
        }
        if (streamConsumed) {
            throw new IllegalStateException("Input stream has already been consumed.");
        }
        streamConsumed = true;
        return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }
    
    /**
     * Get a sub-resource stream by identifier.
     *
     * @param identifier The sub-resource identifier
     * @return Optional containing the input stream if found
     */
    public Optional<InputStream> getSubResource(KeyId identifier) {
        return subResourceProvider.apply(identifier);
    }
    
    /**
     * Get a sub-resource stream by path components.
     * Constructs the identifier from namespace:pathPrefix/path
     *
     * @param namespace  The resource namespace
     * @param pathPrefix The path prefix (e.g., "render/resource/shader")
     * @param path       The resource path
     * @return Optional containing the input stream if found
     */
    public Optional<InputStream> getSubResource(String namespace, String pathPrefix, String path) {
        KeyId subId = KeyId.of(namespace + ":" + pathPrefix + "/" + path);
        return subResourceProvider.apply(subId);
    }
    
    /**
     * Get the sub-resource provider function.
     * Useful for passing to nested loaders.
     *
     * @return The sub-resource provider function
     */
    public Function<KeyId, Optional<InputStream>> getSubResourceProvider() {
        return subResourceProvider;
    }
    
    private String readStreamAsString() {
        if (cachedBytes != null) {
            return new String(cachedBytes, StandardCharsets.UTF_8);
        }
        if (streamConsumed) {
            throw new IllegalStateException("Input stream has already been consumed.");
        }
        streamConsumed = true;
        try {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read input stream as string", e);
        }
    }
    
    private byte[] readStreamAsBytes() {
        if (cachedString != null) {
            return cachedString.getBytes(StandardCharsets.UTF_8);
        }
        if (streamConsumed) {
            throw new IllegalStateException("Input stream has already been consumed.");
        }
        streamConsumed = true;
        try {
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read input stream as bytes", e);
        }
    }
    
    @Override
    public String toString() {
        return "ResourceLoadContext{" +
                "resourceId=" + resourceId +
                ", streamConsumed=" + streamConsumed +
                '}';
    }
}

