package rogo.sketch.render.pipeline.data;

import rogo.sketch.render.pipeline.RenderParameter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages instance offsets for different render parameters.
 */
public class InstancedOffsetData implements RenderPipelineData {
    private final Map<RenderParameter, AtomicInteger> offsets = new HashMap<>();

    /**
     * Get or create an atomic offset counter for the given parameter.
     *
     * @param param The render parameter
     * @return The atomic integer for the offset
     */
    public AtomicInteger get(RenderParameter param) {
        return offsets.computeIfAbsent(param, k -> new AtomicInteger(0));
    }

    /**
     * Get all underlying offsets.
     *
     * @return The map of offsets
     */
    public Map<RenderParameter, AtomicInteger> getAll() {
        return offsets;
    }

    @Override
    public void reset() {
        offsets.clear();
    }
}
