package rogo.sketch.render.resource.loader;

import com.google.gson.Gson;
import rogo.sketch.api.ResourceObject;

/**
 * Interface for loading resources from JSON data
 */
public interface ResourceLoader<T extends ResourceObject> {
    
    /**
     * Load a resource from JSON data
     * 
     * @param jsonData The JSON string containing resource definition
     * @param gson The Gson instance for parsing
     * @return The loaded resource or null if failed
     */
    T loadFromJson(String jsonData, Gson gson);
    
    /**
     * Get the resource class this loader handles
     */
    Class<T> getResourceClass();
}