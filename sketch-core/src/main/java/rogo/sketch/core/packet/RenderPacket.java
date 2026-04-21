package rogo.sketch.core.packet;

import rogo.sketch.core.graphics.ecs.GraphicsUniformSubject;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.shader.uniform.DrawUniformSet;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public interface RenderPacket {
    KeyId stageId();

    PipelineType pipelineType();

    ExecutionKey stateKey();

    ResourceBindingPlan bindingPlan();

    default ResourceSetKey resourceSetKey() {
        return ResourceSetKey.empty();
    }

    default ResourceSetKey resourceBindingKey() {
        return resourceSetKey();
    }

    default UniformGroupSet uniformGroups() {
        return UniformGroupSet.empty();
    }

    default DrawUniformSet drawUniformSet() {
        return uniformGroups().drawUniforms();
    }

    RenderPacketType packetType();

    default RenderPacketKind packetKind() {
        return packetType().kind();
    }

    UniformValueSnapshot uniformSnapshot();

    List<GraphicsUniformSubject> completionSubjects();
}

