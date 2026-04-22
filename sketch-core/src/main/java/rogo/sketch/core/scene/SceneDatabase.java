package rogo.sketch.core.scene;

import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBf;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Host-neutral scene proxy store used by culling-facing systems.
 */
public final class SceneDatabase {
    private final Map<Object, SceneProxy> proxies = new LinkedHashMap<>();

    public synchronized void touchEntityProxy(
            Object hostKey,
            AABBf bounds,
            int flags,
            @Nullable GraphicsEntityId transformEntityId) {
        touchProxy(hostKey, SceneProxy.Kind.ENTITY, bounds, flags, transformEntityId);
    }

    public synchronized void touchBlockEntityProxy(
            Object hostKey,
            AABBf bounds,
            int flags,
            @Nullable GraphicsEntityId transformEntityId) {
        touchProxy(hostKey, SceneProxy.Kind.BLOCK_ENTITY, bounds, flags, transformEntityId);
    }

    public synchronized void replaceTerrainRegionProxies(Iterable<? extends TerrainRegionProxySource> regionSources) {
        clear(SceneProxy.Kind.TERRAIN_REGION);
        if (regionSources == null) {
            return;
        }
        for (TerrainRegionProxySource source : regionSources) {
            if (source == null || source.bounds() == null) {
                continue;
            }
            touchProxy(
                    source.hostKey(),
                    SceneProxy.Kind.TERRAIN_REGION,
                    source.bounds(),
                    0,
                    null);
        }
    }

    public interface TerrainRegionProxySource {
        Object hostKey();

        AABBf bounds();
    }

    public synchronized void touchProxy(
            Object hostKey,
            SceneProxy.Kind kind,
            AABBf bounds,
            int flags,
            @Nullable GraphicsEntityId transformEntityId) {
        if (hostKey == null || kind == null || bounds == null) {
            return;
        }
        proxies.put(hostKey, new SceneProxy(hostKey, kind, bounds, flags, transformEntityId));
    }

    public synchronized @Nullable SceneProxy proxy(Object hostKey) {
        return hostKey != null ? proxies.get(hostKey) : null;
    }

    public synchronized List<SceneProxy> snapshot(SceneProxy.Kind... kinds) {
        if (kinds == null || kinds.length == 0) {
            return List.copyOf(proxies.values());
        }
        EnumSet<SceneProxy.Kind> kindSet = EnumSet.noneOf(SceneProxy.Kind.class);
        for (SceneProxy.Kind kind : kinds) {
            if (kind != null) {
                kindSet.add(kind);
            }
        }
        List<SceneProxy> matches = new ArrayList<>();
        for (SceneProxy proxy : proxies.values()) {
            if (kindSet.contains(proxy.kind())) {
                matches.add(proxy);
            }
        }
        return matches;
    }

    public synchronized List<SceneProxy> snapshotEntities() {
        return snapshot(SceneProxy.Kind.ENTITY);
    }

    public synchronized List<SceneProxy> snapshotBlockEntities() {
        return snapshot(SceneProxy.Kind.BLOCK_ENTITY);
    }

    public synchronized List<SceneProxy> snapshotTerrainRegions() {
        return snapshot(SceneProxy.Kind.TERRAIN_REGION);
    }

    public synchronized void remove(Object hostKey) {
        if (hostKey != null) {
            proxies.remove(hostKey);
        }
    }

    public synchronized void clear(SceneProxy.Kind... kinds) {
        if (kinds == null || kinds.length == 0) {
            proxies.clear();
            return;
        }
        EnumSet<SceneProxy.Kind> kindSet = EnumSet.noneOf(SceneProxy.Kind.class);
        for (SceneProxy.Kind kind : kinds) {
            if (kind != null) {
                kindSet.add(kind);
            }
        }
        proxies.entrySet().removeIf(entry -> kindSet.contains(entry.getValue().kind()));
    }
}
