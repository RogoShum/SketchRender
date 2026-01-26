package rogo.sketch.core.resource.loader;

import com.google.gson.Gson;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.util.KeyId;

import java.io.InputStream;
import java.util.Optional;
import java.util.function.Function;

/**
 * Interface for loading resources from generic data (JSON, Binary, etc.)
 */
public interface ResourceLoader<T extends ResourceObject> {

    /**
     * Load a resource from data
     *
     * @param keyId       The identifier of the resource
     * @param data            The resource data (String, Bytes, Stream)
     * @param gson            The Gson instance for parsing (if needed)
     * @param resourceProvider Provider for sub-resources
     * @return The loaded resource or null if failed
     */
    T load(KeyId keyId, ResourceData data, Gson gson, Function<KeyId, Optional<InputStream>> resourceProvider);

    default T load(KeyId keyId, ResourceData data, Gson gson) {
        return load(keyId, data, gson, id -> Optional.empty());
    }
}