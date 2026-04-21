package rogo.sketch.core.pipeline.flow.ecs;

import org.joml.FrustumIntersection;
import org.joml.primitives.AABBf;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.flow.v2.StageEntityView;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight ECS-native visibility filter. This first cut keeps the logic
 * simple and frame-local: entities with bounds are frustum tested, others are
 * treated as visible.
 */
public final class SpatialIndexSystem<C extends RenderContext> {
    public List<StageEntityView.Entry> collectVisible(List<StageEntityView.Entry> entries, C context) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<StageEntityView.Entry> visible = new ArrayList<>(entries.size());
        FrustumIntersection frustum = context != null ? context.getFrustum() : null;
        for (StageEntityView.Entry entry : entries) {
            AABBf bounds = entry.bounds();
            if (bounds == null || frustum == null) {
                visible.add(entry);
                continue;
            }
            if (frustum.testAab(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ)) {
                visible.add(entry);
            }
        }
        return visible;
    }
}
