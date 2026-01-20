package rogo.sketch.render.pipeline.flow;

import rogo.sketch.render.pipeline.data.RenderPipelineData;
import rogo.sketch.render.pipeline.data.PipelineDataStore;
import rogo.sketch.render.vertex.VertexResourceManager;
import rogo.sketch.util.KeyId;

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
    private final PipelineDataStore backendDataRegistry;

    public RenderFlowContext(VertexResourceManager vertexResourceManager, PipelineDataStore backendDataRegistry) {
        this.vertexResourceManager = vertexResourceManager;
        this.backendDataRegistry = backendDataRegistry;
    }

    /**
     *
     * @return The vertex resource manager
     */
    public VertexResourceManager getResourceManager() {
        return vertexResourceManager;
    }

    public <T extends RenderPipelineData> T getPipelineData(KeyId key) {
        return backendDataRegistry.get(key);
    }
}
