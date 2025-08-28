package rogo.sketch.render.vertex;

import rogo.sketch.render.RenderParameter;
import rogo.sketch.render.resource.buffer.VertexResource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for shared VertexResource instances based on RenderSetting
 * Provides efficient caching and reuse of vertex resources with the same parameters
 */
public class VertexResourceManager {
    private static VertexResourceManager instance;
    private final Map<RenderParameter, VertexResource> resourceCache = new ConcurrentHashMap<>();

    private VertexResourceManager() {
    }

    public static VertexResourceManager getInstance() {
        if (instance == null) {
            instance = new VertexResourceManager();
        }
        return instance;
    }

    /**
     * Get or create a VertexResource for the given RenderSetting
     * Returns a shared instance if one with the same parameters already exists
     */
    public VertexResource getOrCreateVertexResource(RenderParameter parameter) {
        if (parameter.isInvalid()) {
            throw new IllegalArgumentException("RenderSetting must have a valid RenderParameter");
        }

        return resourceCache.computeIfAbsent(parameter, k -> createVertexResource(parameter));
    }

    /**
     * Create a new VertexResource based on RenderParameter
     */
    private VertexResource createVertexResource(RenderParameter parameter) {
        return new VertexResource(
                parameter.dataFormat(),
                null, // No dynamic format for shared resources
                DrawMode.NORMAL,
                parameter.primitiveType(),
                parameter.usage()
        );
    }

    /**
     * Check if a VertexResource exists for the given RenderSetting
     */
    public boolean hasVertexResource(RenderParameter renderParameter) {
        if (renderParameter.isInvalid()) {
            return false;
        }

        return resourceCache.containsKey(renderParameter);
    }

    /**
     * Remove a VertexResource from cache and dispose it
     */
    public void removeVertexResource(RenderParameter renderParameter) {
        if (renderParameter.isInvalid()) {
            return;
        }

        VertexResource resource = resourceCache.remove(renderParameter);
        if (resource != null) {
            resource.dispose();
        }
    }

    /**
     * Clear all cached VertexResources
     */
    public void clearAll() {
        resourceCache.values().forEach(VertexResource::dispose);
        resourceCache.clear();
    }

    /**
     * Get cache statistics for debugging
     */
    public String getCacheStats() {
        return String.format("VertexResourceManager: %d cached resources", resourceCache.size());
    }
}