package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.shader.uniform.UniformGroupSet;

import java.util.List;

public record ResourceGroupSlice(
        Object sourceSlice,
        PipelineStateKey stateKey,
        ResourceBindingPlan bindingPlan,
        ResourceSetKey resourceSetKey,
        UniformGroupSet uniformGroups,
        List<? extends Graphics> graphics
) {
    public ResourceGroupSlice {
        graphics = graphics != null ? List.copyOf(graphics) : List.of();
        uniformGroups = uniformGroups != null ? uniformGroups : UniformGroupSet.empty();
        resourceSetKey = resourceSetKey != null ? resourceSetKey : ResourceSetKey.empty();
    }
}
