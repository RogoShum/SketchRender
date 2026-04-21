package rogo.sketch.core.pipeline.indirect;

import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.packet.PacketBuildContext;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public record GpuIndirectCompileInput(
        KeyId stageId,
        PipelineType pipelineType,
        RenderParameter renderParameter,
        ExecutionKey stateKey,
        ResourceSetKey resourceSetKey,
        GeometryHandleKey geometryHandle,
        List<KeyId> graphicsIds,
        PacketBuildContext packetBuildContext
) {
    public GpuIndirectCompileInput {
        graphicsIds = graphicsIds != null ? List.copyOf(graphicsIds) : List.of();
    }
}
