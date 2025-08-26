package rogo.sketch.render.data.filler;

import rogo.sketch.render.RenderParameter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global manager for VertexFiller instances based on RenderParameter
 * Provides efficient caching and reuse of vertex fillers with the same parameters
 */
public class VertexFillerManager {
    private static VertexFillerManager instance;
    private final Map<RenderParameter, VertexFiller> fillerCache = new ConcurrentHashMap<>();

    private VertexFillerManager() {
    }

    public static VertexFillerManager getInstance() {
        if (instance == null) {
            synchronized (VertexFillerManager.class) {
                if (instance == null) {
                    instance = new VertexFillerManager();
                }
            }
        }
        return instance;
    }

    /**
     * Get or create a VertexFiller for the given RenderParameter
     * Returns a shared instance if one with the same parameters already exists
     */
    public VertexFiller getOrCreateVertexFiller(RenderParameter parameter) {
        if (parameter == null || parameter.isInvalid()) {
            throw new IllegalArgumentException("RenderParameter must be valid");
        }

        return fillerCache.computeIfAbsent(parameter, k -> createVertexFiller(parameter));
    }

    /**
     * Create a new VertexFiller based on RenderParameter
     */
    private VertexFiller createVertexFiller(RenderParameter parameter) {
        VertexFiller filler = new VertexFiller(
                parameter.dataFormat(),
                parameter.primitiveType()
        );

        if (parameter.enableSorting()) {
            filler.enableSorting();
        }

        return filler;
    }

    /**
     * Check if a VertexFiller exists for the given RenderParameter
     */
    public boolean hasVertexFiller(RenderParameter parameter) {
        if (parameter == null || parameter.isInvalid()) {
            return false;
        }

        return fillerCache.containsKey(parameter);
    }

    /**
     * Remove a VertexFiller from cache and dispose it
     */
    public void removeVertexFiller(RenderParameter parameter) {
        if (parameter == null || parameter.isInvalid()) {
            return;
        }

        VertexFiller filler = fillerCache.remove(parameter);
        if (filler != null) {
            filler.dispose();
        }
    }

    /**
     * Clear all cached VertexFillers
     */
    public void clearAll() {
        fillerCache.values().forEach(VertexFiller::dispose);
        fillerCache.clear();
    }

    /**
     * Reset a specific filler for reuse
     */
    public void resetFiller(RenderParameter parameter) {
        if (parameter == null || parameter.isInvalid()) {
            return;
        }

        VertexFiller filler = fillerCache.get(parameter);
        if (filler != null) {
            filler.clear();
        }
    }

    /**
     * Get cache statistics for debugging
     */
    public String getCacheStats() {
        return String.format("VertexFillerManager: %d cached fillers", fillerCache.size());
    }
}
