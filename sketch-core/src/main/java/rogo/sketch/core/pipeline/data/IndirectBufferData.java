package rogo.sketch.core.pipeline.data;

import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.resource.buffer.IndirectCommandBuffer;
import rogo.sketch.core.util.KeyId;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages indirect command buffers for different render parameters.
 */
public class IndirectBufferData implements RenderPipelineData {
    public static final KeyId KEY = KeyId.of("indirect_buffers");
    private final Map<RenderParameter, IndirectCommandBuffer> buffers = new ConcurrentHashMap<>();
    private final Set<RenderParameter> pendingCreate = Collections.newSetFromMap(new ConcurrentHashMap<>());
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
        if (param == null) {
            return null;
        }
        if (GraphicsDriver.getCurrentAPI().isMainThread()) {
            return buffers.computeIfAbsent(param, k -> new IndirectCommandBuffer(defaultBufferSize));
        }
        // Async domain never creates GL buffers; it only reads existing entries.
        return buffers.get(param);
    }

    public void planCreate(RenderParameter param) {
        if (param != null && !buffers.containsKey(param)) {
            pendingCreate.add(param);
        }
    }

    public int materializePending() {
        if (!GraphicsDriver.getCurrentAPI().isMainThread()) {
            return 0;
        }
        int created = 0;
        for (RenderParameter param : pendingCreate) {
            if (!buffers.containsKey(param)) {
                buffers.put(param, new IndirectCommandBuffer(defaultBufferSize));
                created++;
            }
        }
        pendingCreate.clear();
        return created;
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
