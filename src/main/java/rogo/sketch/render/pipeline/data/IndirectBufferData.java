package rogo.sketch.render.pipeline.data;

import rogo.sketch.render.pipeline.RenderParameter;
import rogo.sketch.render.resource.buffer.IndirectCommandBuffer;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages indirect command buffers for different render parameters.
 */
public class IndirectBufferData implements RenderPipelineData {
    private final Map<RenderParameter, IndirectCommandBuffer> buffers = new HashMap<>();
    private final int defaultBufferSize;

    public IndirectBufferData(int defaultBufferSize) {
        this.defaultBufferSize = defaultBufferSize;
    }

    public IndirectBufferData() {
        this(1280);
    }

    /**
     * Get or create an indirect command buffer for the given parameter.
     *
     * @param param The render parameter
     * @return The indirect command buffer
     */
    public IndirectCommandBuffer get(RenderParameter param) {
        return buffers.computeIfAbsent(param, k -> new IndirectCommandBuffer(defaultBufferSize));
    }

    /**
     * Get all underlying buffers (read-only view ideally, but map for now).
     *
     * @return The map of buffers
     */
    public Map<RenderParameter, IndirectCommandBuffer> getAll() {
        return buffers;
    }

    @Override
    public void reset() {
        for (IndirectCommandBuffer buffer : buffers.values()) {
            buffer.clear();
        }
    }
}
