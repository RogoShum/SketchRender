package rogo.sketch.feature.culling;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import rogo.sketch.module.culling.entity.EntityVisibilityQueryService;

import java.util.Collection;

public final class CullingVisibilitySettingsSnapshot {
    private static final CullingVisibilitySettingsSnapshot DISABLED = new CullingVisibilitySettingsSnapshot(
            false,
            false,
            false,
            new ObjectOpenHashSet<>(),
            new ObjectOpenHashSet<>(),
            null);

    private final boolean cullEntity;
    private final boolean cullBlockEntity;
    private final boolean asyncChunkRebuild;
    private final ObjectOpenHashSet<String> entitySkipSet;
    private final ObjectOpenHashSet<String> blockEntitySkipSet;
    private final EntityVisibilityQueryService queries;

    public CullingVisibilitySettingsSnapshot(
            boolean cullEntity,
            boolean cullBlockEntity,
            boolean asyncChunkRebuild,
            Collection<String> entitySkipSet,
            Collection<String> blockEntitySkipSet,
            EntityVisibilityQueryService queries) {
        this.cullEntity = cullEntity;
        this.cullBlockEntity = cullBlockEntity;
        this.asyncChunkRebuild = asyncChunkRebuild;
        this.entitySkipSet = new ObjectOpenHashSet<>(entitySkipSet);
        this.blockEntitySkipSet = new ObjectOpenHashSet<>(blockEntitySkipSet);
        this.entitySkipSet.trim();
        this.blockEntitySkipSet.trim();
        this.queries = queries;
    }

    public static CullingVisibilitySettingsSnapshot disabled() {
        return DISABLED;
    }

    public boolean cullEntity() {
        return cullEntity;
    }

    public boolean cullBlockEntity() {
        return cullBlockEntity;
    }

    public boolean asyncChunkRebuild() {
        return asyncChunkRebuild;
    }

    public EntityVisibilityQueryService queries() {
        return queries;
    }

    public boolean entitySkipContains(String identifier) {
        return identifier != null && entitySkipSet.contains(identifier);
    }

    public boolean blockEntitySkipContains(String identifier) {
        return identifier != null && blockEntitySkipSet.contains(identifier);
    }

    public CullingVisibilitySettingsSnapshot withQueries(EntityVisibilityQueryService nextQueries) {
        if (queries == nextQueries) {
            return this;
        }
        return new CullingVisibilitySettingsSnapshot(
                cullEntity,
                cullBlockEntity,
                asyncChunkRebuild,
                entitySkipSet,
                blockEntitySkipSet,
                nextQueries);
    }
}
