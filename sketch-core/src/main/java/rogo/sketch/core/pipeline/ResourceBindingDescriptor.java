package rogo.sketch.core.pipeline;

import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public record ResourceBindingDescriptor(
        KeyId resourceLayoutKey,
        int resourceBindingHash,
        List<ResourceBindingPlan.BindingEntry> entries
) {
    public ResourceBindingDescriptor {
        resourceLayoutKey = resourceLayoutKey != null ? resourceLayoutKey : KeyId.of("sketch:empty_resource_layout");
        entries = entries != null ? List.copyOf(entries) : List.of();
    }

    public static ResourceBindingDescriptor from(ResourceBindingPlan bindingPlan) {
        ResourceBindingPlan plan = bindingPlan != null ? bindingPlan : ResourceBindingPlan.empty();
        return new ResourceBindingDescriptor(plan.layoutKey(), plan.resourceBindingHash(), plan.entries());
    }
}
