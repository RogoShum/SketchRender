package rogo.sketch.core.scene;

import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBf;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;

public record SceneProxy(
        Object hostKey,
        Kind kind,
        AABBf bounds,
        int flags,
        @Nullable GraphicsEntityId transformEntityId) {
    public SceneProxy {
        bounds = bounds != null ? new AABBf(bounds) : null;
    }

    public boolean hasFiniteBounds() {
        return bounds != null
                && Float.isFinite(bounds.minX)
                && Float.isFinite(bounds.minY)
                && Float.isFinite(bounds.minZ)
                && Float.isFinite(bounds.maxX)
                && Float.isFinite(bounds.maxY)
                && Float.isFinite(bounds.maxZ);
    }

    public enum Kind {
        ENTITY,
        BLOCK_ENTITY,
        TERRAIN_REGION,
        TERRAIN_SECTION
    }
}
