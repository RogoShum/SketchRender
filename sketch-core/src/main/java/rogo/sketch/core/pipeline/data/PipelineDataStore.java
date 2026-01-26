package rogo.sketch.core.pipeline.data;

import rogo.sketch.core.util.KeyId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A registry that holds multiple {@link RenderPipelineData} instances.
 * <p>
 * This class acts as a central container for all per-frame render data
 * structures,
 * allowing unified management (like resetting) and retrieval.
 * </p>
 */
public class PipelineDataStore {
    private final Map<KeyId, RenderPipelineData> dataMap = new LinkedHashMap<>();

    /**
     * Register a new data instance.
     *
     * @param key  The key to identify the data
     * @param data The data instance
     */
    public void register(KeyId key, RenderPipelineData data) {
        dataMap.put(key, data);
    }

    /**
     * Get a registered data instance.
     *
     * @param key The key
     * @param <T> The type of the data
     * @return The data instance, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T extends RenderPipelineData> T get(KeyId key) {
        return (T) dataMap.get(key);
    }

    public void reset() {
        for (RenderPipelineData data : dataMap.values()) {
            data.reset();
        }
    }

    /**
     * Get all registered data instances.
     *
     * @return Collection of all data instances
     */
    public Collection<RenderPipelineData> getAll() {
        return dataMap.values();
    }
}