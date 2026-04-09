package rogo.sketch.core.packet;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public record ClearPacket(
        KeyId stageId,
        PipelineType pipelineType,
        PipelineStateKey stateKey,
        ResourceBindingPlan bindingPlan,
        UniformValueSnapshot uniformSnapshot,
        List<? extends Graphics> completionGraphics,
        KeyId renderTargetId,
        List<Object> colorAttachments,
        boolean clearColor,
        boolean clearDepth,
        float[] colorValue,
        float depthValue,
        boolean[] colorMask,
        boolean restorePreviousRenderTarget
) implements RenderPacket {
    private static final RenderPacketType TYPE = RenderPacketType.CLEAR;

    @Override
    public RenderPacketType packetType() {
        return TYPE;
    }
}

