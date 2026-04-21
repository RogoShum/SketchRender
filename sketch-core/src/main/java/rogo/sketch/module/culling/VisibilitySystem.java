package rogo.sketch.module.culling;

import org.joml.FrustumIntersection;
import org.joml.primitives.AABBf;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.flow.ecs.SpatialIndexSystem;
import rogo.sketch.core.pipeline.flow.v2.StageEntityView;
import rogo.sketch.core.scene.SceneDatabase;
import rogo.sketch.core.scene.SceneProxy;
import rogo.sketch.module.culling.TerrainRegionSource;
import rogo.sketch.module.culling.entity.EntitySourceRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Coarse visibility system that bridges ECS bounds indexing and scene proxy
 * candidates. Stage 6 keeps this intentionally lightweight and host-neutral.
 */
public final class VisibilitySystem {
    private final SpatialIndexSystem<RenderContext> spatialIndexSystem = new SpatialIndexSystem<>();

    public <C extends RenderContext> List<StageEntityView.Entry> collectVisibleGraphics(
            List<StageEntityView.Entry> entries,
            C context) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return List.copyOf(spatialIndexSystem.collectVisible(entries, context));
    }

    public List<EntitySourceRegistry.IndexedSubject> collectVisibleSubjects(
            List<EntitySourceRegistry.IndexedSubject> subjects,
            RenderContext renderContext) {
        if (subjects == null || subjects.isEmpty()) {
            return List.of();
        }
        List<EntitySourceRegistry.IndexedSubject> visible = new ArrayList<>(subjects.size());
        for (EntitySourceRegistry.IndexedSubject subject : subjects) {
            if (subject != null && subject.data() != null && isVisible(subject.data().bounds(), renderContext)) {
                visible.add(subject);
            }
        }
        return visible;
    }

    public List<SceneProxy> collectVisibleProxies(
            SceneDatabase sceneDatabase,
            RenderContext renderContext,
            SceneProxy.Kind... kinds) {
        if (sceneDatabase == null) {
            return List.of();
        }
        List<SceneProxy> proxies = sceneDatabase.snapshot(kinds);
        List<SceneProxy> visible = new ArrayList<>(proxies.size());
        for (SceneProxy proxy : proxies) {
            if (isVisible(proxy.bounds(), renderContext)) {
                visible.add(proxy);
            }
        }
        return visible;
    }

    public List<TerrainRegionSource> collectVisibleTerrainRegions(
            SceneDatabase sceneDatabase,
            List<TerrainRegionSource> terrainSources,
            RenderContext renderContext) {
        if (terrainSources == null || terrainSources.isEmpty()) {
            return List.of();
        }
        // Sodium terrain sources already come from a host-side visible region list.
        // Re-filtering here is redundant, can destabilize ordering, and has caused
        // incorrect rejections when region-space bounds drift from the host queue.
        return List.copyOf(terrainSources);
    }

    private boolean isVisible(AABBf bounds, RenderContext renderContext) {
        if (bounds == null) {
            return false;
        }
        if (!hasFiniteBounds(bounds)) {
            return false;
        }
        FrustumIntersection frustum = renderContext != null ? renderContext.getFrustum() : null;
        if (frustum == null) {
            return true;
        }
        return frustum.testAab(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
    }

    private boolean hasFiniteBounds(AABBf bounds) {
        return Float.isFinite(bounds.minX)
                && Float.isFinite(bounds.minY)
                && Float.isFinite(bounds.minZ)
                && Float.isFinite(bounds.maxX)
                && Float.isFinite(bounds.maxY)
                && Float.isFinite(bounds.maxZ);
    }
}
