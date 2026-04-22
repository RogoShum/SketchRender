package rogo.sketch.core.packet;

import rogo.sketch.core.graphics.ecs.GraphicsUniformSubject;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public record DrawPacket(
        KeyId stageId,
        PipelineType pipelineType,
        RasterPipelineKey stateKey,
        ResourceBindingPlan bindingPlan,
        ResourceSetKey resourceSetKey,
        UniformGroupSet uniformGroups,
        List<GraphicsUniformSubject> completionSubjects,
        GeometryHandleKey geometryHandle,
        DrawPlan drawPlan
) implements RenderPacket {
    private static final RenderPacketType TYPE = RenderPacketType.DRAW;

    public DrawPacket {
        resourceSetKey = resourceSetKey != null ? resourceSetKey : ResourceSetKey.empty();
        uniformGroups = uniformGroups != null ? uniformGroups : UniformGroupSet.empty();
    }

    @Override
    public RenderPacketType packetType() {
        return TYPE;
    }

    public UniformValueSnapshot uniformSnapshot() {
        if (!uniformGroups.drawUniforms().isEmpty()) {
            return uniformGroups.drawUniforms().snapshot();
        }
        return uniformGroups.resourceUniforms().snapshot();
    }
}

