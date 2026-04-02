package rogo.sketch.core.packet;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.information.ComputeInstanceInfo;
import rogo.sketch.core.shader.ComputeShader;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.util.KeyId;

import java.util.List;
import java.util.function.BiConsumer;

public record DispatchPacket(
        KeyId stageId,
        PipelineType pipelineType,
        PipelineStateKey stateKey,
        ResourceBindingPlan bindingPlan,
        UniformValueSnapshot uniformSnapshot,
        List<? extends Graphics> completionGraphics,
        int workGroupsX,
        int workGroupsY,
        int workGroupsZ,
        ComputeInstanceInfo computeInfo,
        BiConsumer<RenderContext, ComputeShader> dispatchFunction
) implements RenderPacket {
}
