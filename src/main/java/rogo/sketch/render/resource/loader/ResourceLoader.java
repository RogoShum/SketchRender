package rogo.sketch.render.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import rogo.sketch.api.ResourceObject;
import rogo.sketch.util.Identifier;

import java.io.BufferedReader;
import java.util.Optional;
import java.util.function.Function;

/**
 * Interface for loading resources from JSON data
 */
public interface ResourceLoader<T extends ResourceObject> {

    /**
     * Load a resource from JSON data
     *
     * @param jsonData The JSON string containing resource definition
     * @param gson     The Gson instance for parsing
     * @return The loaded resource or null if failed
     */
    T loadFromJson(Identifier identifier, String jsonData, Gson gson, Function<Identifier, Optional<BufferedReader>> resourceProvider);

    default T loadFromJson(Identifier identifier, String jsonData, Gson gson) {
        return loadFromJson(identifier, jsonData, gson, id -> Optional.empty());
    }
}