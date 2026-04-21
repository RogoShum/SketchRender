package rogo.sketch.core.object;

import rogo.sketch.core.graphics.ecs.GraphicsEntityId;

/**
 * Host-object membership sync event used to keep the object registry and
 * root-handle mapping alive without re-spawning the graphics entity.
 */
public record ObjectSyncEvent(
        Object hostObject,
        ObjectHostKind hostKind,
        ObjectGraphicsHandle handle,
        ObjectGraphicsRootRole rootRole,
        GraphicsEntityId rootEntityId,
        int logicTick
) {
}
