package rogo.sketch.core.packet;

import rogo.sketch.core.pipeline.data.PipelineDataStore;
import rogo.sketch.core.pipeline.data.RenderPipelineData;
import rogo.sketch.core.pipeline.data.GeometryFrameData;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.VertexResourceManager;

public class PacketBuildContext {
    private final PipelineType pipelineType;
    private final VertexResourceManager vertexResourceManager;
    private final PipelineDataStore pipelineDataStore;

    public PacketBuildContext(
            PipelineType pipelineType,
            VertexResourceManager vertexResourceManager,
            PipelineDataStore pipelineDataStore) {
        this.pipelineType = pipelineType;
        this.vertexResourceManager = vertexResourceManager;
        this.pipelineDataStore = pipelineDataStore;
    }

    public PipelineType pipelineType() {
        return pipelineType;
    }

    public VertexResourceManager getResourceManager() {
        return vertexResourceManager;
    }

    public <T extends RenderPipelineData> T getPipelineData(KeyId key) {
        return pipelineDataStore.get(key);
    }

    public GeometryFrameData geometryFrameData() {
        return getPipelineData(GeometryFrameData.KEY);
    }
}
