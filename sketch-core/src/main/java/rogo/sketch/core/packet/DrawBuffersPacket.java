package rogo.sketch.core.packet;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public record DrawBuffersPacket(
        KeyId stageId,
        PipelineType pipelineType,
        PipelineStateKey stateKey,
        ResourceBindingPlan bindingPlan,
        UniformValueSnapshot uniformSnapshot,
        List<? extends Graphics> completionGraphics,
        KeyId renderTargetId,
        List<Object> colorComponents
) implements RenderPacket {
}
