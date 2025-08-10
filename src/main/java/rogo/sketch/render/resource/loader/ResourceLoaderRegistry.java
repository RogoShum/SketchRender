package rogo.sketch.render.resource.loader;

import rogo.sketch.api.ResourceObject;
import rogo.sketch.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for resource loaders
 */
public class ResourceLoaderRegistry {
    
    private final Map<Identifier, ResourceLoader<?>> loaders = new ConcurrentHashMap<>();
    
    /**
     * Register a resource loader for a specific type
     */
    public <T extends ResourceObject> void registerLoader(Identifier type, ResourceLoader<T> loader) {
        loaders.put(type, loader);
    }
    
    /**
     * Get a resource loader for a specific type
     */
    @SuppressWarnings("unchecked")
    public <T extends ResourceObject> ResourceLoader<T> getLoader(Identifier type) {
        return (ResourceLoader<T>) loaders.get(type);
    }
    
    /**
     * Check if a loader exists for a specific type
     */
    public boolean hasLoader(Identifier type) {
        return loaders.containsKey(type);
    }
    
    /**
     * Remove a loader for a specific type
     */
    public void removeLoader(Identifier type) {
        loaders.remove(type);
    }
    
    /**
     * Get all registered loader types
     */
    public java.util.Set<Identifier> getRegisteredTypes() {
        return loaders.keySet();
    }
} 