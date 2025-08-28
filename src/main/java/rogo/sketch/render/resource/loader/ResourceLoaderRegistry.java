package rogo.sketch.render.resource.loader;

import rogo.sketch.api.ResourceObject;
import rogo.sketch.util.Identifier;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for resource loaders
 */
public class ResourceLoaderRegistry<T extends ResourceObject> {

    private final Map<Identifier, Set<ResourceLoader<T>>> loaders = new ConcurrentHashMap<>();

    /**
     * Register a resource loader for a specific type
     */
    public void registerLoader(Identifier type, ResourceLoader<T> loader) {
        loaders.computeIfAbsent(type, k -> new HashSet<>()).add(loader);
    }

    /**
     * Get a resource loader for a specific type
     */
    @SuppressWarnings("unchecked")
    public Set<ResourceLoader<T>> getLoader(Identifier type) {
        return loaders.get(type);
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
    public Set<Identifier> getRegisteredTypes() {
        return loaders.keySet();
    }
} 