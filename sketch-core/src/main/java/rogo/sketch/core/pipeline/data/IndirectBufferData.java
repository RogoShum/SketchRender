package rogo.sketch.core.pipeline.data;

import rogo.sketch.core.backend.BackendBufferFactory;
import rogo.sketch.core.backend.BackendIndirectBuffer;
import rogo.sketch.core.pipeline.indirect.PersistentIndirectBufferPool;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.resource.descriptor.BufferRole;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
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
    private final PersistentIndirectBufferPool pool;
    private final int defaultBufferSize;

    public IndirectBufferData(int defaultBufferSize) {
        this.defaultBufferSize = defaultBufferSize;
        this.pool = new PersistentIndirectBufferPool(defaultBufferSize);
    }

    public IndirectBufferData() {
        this(1280);
    }

    /**
     * Get an existing indirect command buffer for the given parameter.
     *
     * @param param The render parameter
     * @return The indirect command buffer, or null if it has not been materialized yet
     */
    public BackendIndirectBuffer get(RenderParameter param) {
        return pool.get(param);
    }

    public BackendIndirectBuffer getOrCreate(RenderParameter param) {
        return pool.getOrCreate(param);
    }

    public void planCreate(RenderParameter param) {
        pool.planCreate(param);
    }

    public int materializePending() {
        return pool.materializePending();
    }

    /**
     * Get all underlying buffers (read-only view ideally, but map for now).
     *
     * @return The map of buffers
     */
    public Map<RenderParameter, BackendIndirectBuffer> getAll() {
        return pool.buffersView();
    }

    public PersistentIndirectBufferPool pool() {
        return pool;
    }

    public void beginFrame() {
        pool.beginFrame();
    }

    public void finishFrame() {
        pool.finishFrame();
    }

    public void synchronizeLayoutsFrom(IndirectBufferData other) {
        if (other == null || other == this) {
            return;
        }
        pool.synchronizeLayoutsFrom(other.pool);
    }

    @Override
    public void reset() {
        beginFrame();
    }
}

