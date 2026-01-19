package rogo.sketch.render.resource.loader;

import rogo.sketch.api.ResourceObject;
import rogo.sketch.util.KeyId;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for resource loaders
 */
public class ResourceLoaderRegistry<T extends ResourceObject> {

    private final Map<KeyId, Set<ResourceLoader<T>>> loaders = new ConcurrentHashMap<>();

    /**
     * Register a resource loader for a specific type
     */
    public void registerLoader(KeyId type, ResourceLoader<T> loader) {
        loaders.computeIfAbsent(type, k -> new HashSet<>()).add(loader);
    }

    /**
     * Get a resource loader for a specific type
     */
    @SuppressWarnings("unchecked")
    public Set<ResourceLoader<T>> getLoader(KeyId type) {
        return loaders.get(type);
    }

    /**
     * Check if a loader exists for a specific type
     */
    public boolean hasLoader(KeyId type) {
        return loaders.containsKey(type);
    }

    /**
     * Remove a loader for a specific type
     */
    public void removeLoader(KeyId type) {
        loaders.remove(type);
    }

    /**
     * Get all registered loader types
     */
    public Set<KeyId> getRegisteredTypes() {
        return loaders.keySet();
    }
} 