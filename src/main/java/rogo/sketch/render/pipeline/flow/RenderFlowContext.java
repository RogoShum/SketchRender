package rogo.sketch.render.pipeline.flow;

import rogo.sketch.render.pipeline.RenderParameter;
import rogo.sketch.render.resource.buffer.IndirectCommandBuffer;
import rogo.sketch.render.vertex.VertexResourceManager;
import rogo.sketch.util.KeyId;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Context object providing resources and utilities for render flow processing.
 * <p>
 * This context is passed to {@link RenderFlowStrategy#createRenderCommands} to
 * provide
 * access to shared resources like vertex managers and indirect buffers.
 * </p>
 */
public class RenderFlowContext {
    private final VertexResourceManager vertexResourceManager;
    private final Map<RenderParameter, IndirectCommandBuffer> indirectBuffers;
    private final Map<RenderParameter, AtomicInteger> instancedOffsets;
    private final Map<KeyId, Object> extraContexts;

    public RenderFlowContext(VertexResourceManager vertexResourceManager, Map<RenderParameter, IndirectCommandBuffer> indirectBuffers, Map<RenderParameter, AtomicInteger> instancedOffsets) {
        this.vertexResourceManager = vertexResourceManager;
        this.indirectBuffers = indirectBuffers;
        this.instancedOffsets = instancedOffsets;
        this.extraContexts = new HashMap<>();
    }

    /**
     * Get the vertex resource manager for allocating and managing vertex buffers.
     *
     * @return The vertex resource manager
     */
    public VertexResourceManager getVertexResourceManager() {
        return vertexResourceManager;
    }

    /**
     * Alias for {@link #getVertexResourceManager()} for compatibility.
     *
     * @return The vertex resource manager
     */
    public VertexResourceManager getResourceManager() {
        return vertexResourceManager;
    }

    /**
     * Get the indirect command buffer for a specific render parameter.
     * Creates a new buffer if one doesn't exist.
     *
     * @param param The render parameter to get the buffer for
     * @return The indirect command buffer
     */
    public IndirectCommandBuffer getOrCreateIndirectBuffer(RenderParameter param) {
        return indirectBuffers.computeIfAbsent(param, k -> new IndirectCommandBuffer(1280));
    }

    /**
     * Get all indirect buffers.
     *
     * @return Map of render parameters to indirect buffers
     */
    public Map<RenderParameter, IndirectCommandBuffer> indirectBuffers() {
        return indirectBuffers;
    }

    public Map<RenderParameter, AtomicInteger> instancedOffsets() {
        return instancedOffsets;
    }

    public Map<KeyId, Object> extraContext() {
        return extraContexts;
    }

    /**
     * Clear all indirect buffers.
     */
    public void clearIndirectBuffers() {
        indirectBuffers.values().forEach(IndirectCommandBuffer::clear);
    }

    /**
     * Upload all dirty indirect buffers.
     */
    public void uploadIndirectBuffers() {
        indirectBuffers.values().forEach(buffer -> {
            buffer.bind();
            buffer.upload();
        });
        IndirectCommandBuffer.unBind();
    }
}
