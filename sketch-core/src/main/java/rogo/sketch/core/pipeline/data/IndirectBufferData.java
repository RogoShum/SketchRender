package rogo.sketch.core.pipeline.data;

import rogo.sketch.core.backend.BackendBufferFactory;
import rogo.sketch.core.backend.BackendIndirectBuffer;
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
    private final Map<RenderParameter, BackendIndirectBuffer> buffers = new ConcurrentHashMap<>();
    private final Set<RenderParameter> pendingCreate = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final int defaultBufferSize;

    public IndirectBufferData(int defaultBufferSize) {
        this.defaultBufferSize = defaultBufferSize;
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
        return param == null ? null : buffers.get(param);
    }

    public BackendIndirectBuffer getOrCreate(RenderParameter param) {
        if (param == null) {
            return null;
        }
        return buffers.computeIfAbsent(param, this::createIndirectBuffer);
    }

    public void planCreate(RenderParameter param) {
        if (param != null && !buffers.containsKey(param)) {
            pendingCreate.add(param);
        }
    }

    public int materializePending() {
        int created = 0;
        for (RenderParameter param : pendingCreate) {
            if (!buffers.containsKey(param)) {
                buffers.put(param, createIndirectBuffer(param));
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
    public Map<RenderParameter, BackendIndirectBuffer> getAll() {
        return buffers;
    }

    @Override
    public void reset() {
        for (BackendIndirectBuffer buffer : buffers.values()) {
            buffer.clear();
        }
    }

    private BackendIndirectBuffer createIndirectBuffer(RenderParameter param) {
        KeyId resourceId = KeyId.of("indirect_" + Integer.toUnsignedLong(param != null ? param.hashCode() : 0));
        ResolvedBufferResource descriptor = new ResolvedBufferResource(
                resourceId,
                BufferRole.INDIRECT,
                BufferUpdatePolicy.DYNAMIC,
                defaultBufferSize,
                BackendIndirectBuffer.COMMAND_STRIDE_BYTES,
                (long) defaultBufferSize * BackendIndirectBuffer.COMMAND_STRIDE_BYTES);
        return BackendBufferFactory.createIndirectBuffer(resourceId, descriptor, defaultBufferSize);
    }
}

