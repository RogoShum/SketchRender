package rogo.sketch.core.object;

import rogo.sketch.core.graphics.ecs.GraphicsEntityId;

/**
 * Lifecycle event emitted before a root graphics entity is destroyed.
 */
public record ObjectDespawnEvent(
        Object hostObject,
        ObjectHostKind hostKind,
        ObjectGraphicsHandle handle,
        ObjectGraphicsRootRole rootRole,
        GraphicsEntityId rootEntityId,
        int logicTick
) {
}
