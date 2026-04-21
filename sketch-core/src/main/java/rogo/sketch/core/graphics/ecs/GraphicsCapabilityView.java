package rogo.sketch.core.graphics.ecs;

import rogo.sketch.core.util.KeyId;

import java.util.List;
import java.util.Set;

/**
 * Editor-facing capability summary derived from ECS component signatures.
 */
public record GraphicsCapabilityView(
        Set<KeyId> componentIds,
        List<GraphicsCapabilityDescriptor> capabilities,
        List<GraphicsAuthoringDescriptor> authoringDescriptors
) {
    public boolean hasCapability(GraphicsCapabilityId capabilityId) {
        if (capabilityId == null) {
            return false;
        }
        for (GraphicsCapabilityDescriptor descriptor : capabilities) {
            if (descriptor != null && capabilityId.equals(descriptor.capabilityId())) {
                return true;
            }
        }
        return false;
    }
}
