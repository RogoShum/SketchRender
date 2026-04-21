package rogo.sketch.core.packet;

import rogo.sketch.core.graphics.ecs.GraphicsUniformSubject;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public record GenerateMipmapPacket(
        KeyId stageId,
        PipelineType pipelineType,
        TransferPlanKey stateKey,
        ResourceBindingPlan bindingPlan,
        UniformValueSnapshot uniformSnapshot,
        List<GraphicsUniformSubject> completionSubjects,
        KeyId textureId
) implements RenderPacket {
    private static final RenderPacketType TYPE = RenderPacketType.GENERATE_MIPMAP;

    @Override
    public RenderPacketType packetType() {
        return TYPE;
    }
}

