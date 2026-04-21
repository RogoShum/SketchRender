package rogo.sketch.core.graphics.ecs;

import rogo.sketch.core.util.KeyId;

public record GraphicsCapabilityDescriptor(
        GraphicsCapabilityId capabilityId,
        KeyId sourceComponentId,
        boolean raster,
        boolean compute,
        boolean function,
        GraphicsUpdateDomain updateDomain,
        boolean editorExposed,
        GraphicsAuthoringDescriptor authoring
) {
}
