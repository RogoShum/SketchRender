package rogo.sketch.core.graphics.ecs;

import rogo.sketch.core.util.KeyId;

public record GraphicsAuthoringDescriptor(
        GraphicsCapabilityId capabilityId,
        KeyId sourceComponentId,
        boolean bounds,
        boolean transform,
        boolean instance,
        boolean uniform,
        boolean ssbo
) {
}
