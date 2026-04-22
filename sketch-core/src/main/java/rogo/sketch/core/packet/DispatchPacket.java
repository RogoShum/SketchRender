package rogo.sketch.core.packet;

import rogo.sketch.core.api.graphics.ComputeDispatchCommand;
import rogo.sketch.core.graphics.ecs.GraphicsUniformSubject;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.information.ComputeInstanceInfo;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public record DispatchPacket(
        KeyId stageId,
        PipelineType pipelineType,
        ComputePipelineKey stateKey,
        ResourceBindingPlan bindingPlan,
        ResourceSetKey resourceSetKey,
        // Compute dispatch retains a packet-local snapshot because its build-time
        // uniforms are not recoverable from the narrowed RenderPacket interface.
        UniformValueSnapshot uniformSnapshot,
        List<GraphicsUniformSubject> completionSubjects,
        int workGroupsX,
        int workGroupsY,
        int workGroupsZ,
        ComputeInstanceInfo computeInfo,
        ComputeDispatchCommand dispatchCommand
) implements RenderPacket {
    private static final RenderPacketType TYPE = RenderPacketType.DISPATCH;

    public DispatchPacket {
        resourceSetKey = resourceSetKey != null ? resourceSetKey : ResourceSetKey.empty();
    }

    @Override
    public RenderPacketType packetType() {
        return TYPE;
    }
}

