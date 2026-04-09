package rogo.sketch.core.packet;

import rogo.sketch.core.pipeline.data.PipelineDataStore;
import rogo.sketch.core.pipeline.data.RenderPipelineData;
import rogo.sketch.core.pipeline.data.GeometryFrameData;
import rogo.sketch.core.pipeline.data.IndirectBufferData;
import rogo.sketch.core.pipeline.data.IndirectPlanData;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.GeometryResourceCoordinator;

public class PacketBuildContext {
    private final PipelineType pipelineType;
    private final GeometryResourceCoordinator geometryResourceCoordinator;
    private final PipelineDataStore pipelineDataStore;

    public PacketBuildContext(
            PipelineType pipelineType,
            GeometryResourceCoordinator geometryResourceCoordinator,
            PipelineDataStore pipelineDataStore) {
        this.pipelineType = pipelineType;
        this.geometryResourceCoordinator = geometryResourceCoordinator;
        this.pipelineDataStore = pipelineDataStore;
    }

    public PipelineType pipelineType() {
        return pipelineType;
    }

    public GeometryResourceCoordinator getResourceManager() {
        return geometryResourceCoordinator;
    }

    public <T extends RenderPipelineData> T getPipelineData(KeyId key) {
        return pipelineDataStore.get(key);
    }

    public GeometryFrameData geometryFrameData() {
        return getPipelineData(GeometryFrameData.KEY);
    }

    public IndirectBufferData indirectBufferData() {
        return getPipelineData(IndirectBufferData.KEY);
    }

    public IndirectPlanData indirectPlanData() {
        return getPipelineData(IndirectPlanData.KEY);
    }
}

