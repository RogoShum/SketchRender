package rogo.sketch.core.pipeline.flow.v2;

import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBf;

public record VisibilityMetadata(
        @Nullable AABBf bounds,
        @Nullable Object sortKey,
        long orderHint,
        int layerHint
) {
    public VisibilityMetadata {
        bounds = bounds != null ? new AABBf(bounds) : null;
    }
}
