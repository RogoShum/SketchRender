package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.shader.uniform.UniformGroupSet;

import java.util.List;

public record ResourceGroupSlice(
        Object sourceSlice,
        ExecutionKey stateKey,
        ResourceBindingPlan bindingPlan,
        ResourceSetKey resourceSetKey,
        UniformGroupSet uniformGroups,
        List<StageEntityView.Entry> entries
) {
    public ResourceGroupSlice {
        entries = entries != null ? List.copyOf(entries) : List.of();
        uniformGroups = uniformGroups != null ? uniformGroups : UniformGroupSet.empty();
        resourceSetKey = resourceSetKey != null ? resourceSetKey : ResourceSetKey.empty();
    }
}
