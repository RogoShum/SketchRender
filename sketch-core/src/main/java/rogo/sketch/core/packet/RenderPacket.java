package rogo.sketch.core.packet;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.shader.uniform.DrawUniformSet;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public interface RenderPacket {
    KeyId stageId();

    PipelineType pipelineType();

    PipelineStateKey stateKey();

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

    UniformValueSnapshot uniformSnapshot();

    List<? extends Graphics> completionGraphics();
}
