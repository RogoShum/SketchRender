package rogo.sketch.core.packet;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public record DrawPacket(
        KeyId stageId,
        PipelineType pipelineType,
        PipelineStateKey stateKey,
        ResourceBindingPlan bindingPlan,
        ResourceSetKey resourceSetKey,
        UniformGroupSet uniformGroups,
        List<? extends Graphics> completionGraphics,
        GeometryHandleKey geometryHandle,
        DrawPlan drawPlan
) implements RenderPacket {
    public DrawPacket {
        resourceSetKey = resourceSetKey != null ? resourceSetKey : ResourceSetKey.empty();
        uniformGroups = uniformGroups != null ? uniformGroups : UniformGroupSet.empty();
    }

    @Override
    public UniformValueSnapshot uniformSnapshot() {
        if (!uniformGroups.drawUniforms().isEmpty()) {
            return uniformGroups.drawUniforms().legacySnapshot();
        }
        return uniformGroups.resourceUniforms().legacySnapshot();
    }
}
