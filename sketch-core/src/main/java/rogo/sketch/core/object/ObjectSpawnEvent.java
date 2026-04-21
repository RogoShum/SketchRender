package rogo.sketch.core.object;

import org.joml.primitives.AABBf;
import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsEntitySchema;

/**
 * Lifecycle event emitted when a host object first materializes a root
 * graphics entity.
 */
public record ObjectSpawnEvent(
        Object hostObject,
        ObjectHostKind hostKind,
        ObjectGraphicsHandle handle,
        ObjectGraphicsRootRole rootRole,
        GraphicsEntityId rootEntityId,
        GraphicsEntitySchema rootSchema,
        @Nullable AABBf bounds,
        int flags,
        int logicTick
) {
}
