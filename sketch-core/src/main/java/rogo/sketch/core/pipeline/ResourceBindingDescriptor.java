package rogo.sketch.core.pipeline;

import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.util.KeyId;

public record ResourceBindingDescriptor(
        KeyId resourceLayoutKey,
        int resourceBindingHash,
        ResourceBindingPlan.BindingEntry[] entries
) {
    public ResourceBindingDescriptor {
        resourceLayoutKey = resourceLayoutKey != null ? resourceLayoutKey : KeyId.of("sketch:empty_resource_layout");
        entries = entries != null ? entries.clone() : new ResourceBindingPlan.BindingEntry[0];
    }

    public static ResourceBindingDescriptor from(ResourceBindingPlan bindingPlan) {
        ResourceBindingPlan plan = bindingPlan != null ? bindingPlan : ResourceBindingPlan.empty();
        return new ResourceBindingDescriptor(plan.layoutKey(), plan.resourceBindingHash(), plan.entries());
    }
}
