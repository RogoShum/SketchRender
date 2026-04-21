package rogo.sketch.module.culling.entity;

import org.joml.primitives.AABBf;

/**
 * Core-owned read-only query surface consumed by host bridges.
 */
public final class EntityVisibilityQueryService {
    private final EntitySourceRegistry sourceRegistry;
    private final EntityMaskStateStore stateStore;

    public EntityVisibilityQueryService(EntitySourceRegistry sourceRegistry, EntityMaskStateStore stateStore) {
        this.sourceRegistry = sourceRegistry;
        this.stateStore = stateStore;
    }

    public boolean shouldSkipEntity(Object subject, AABBf bounds, int flags, int logicTick) {
        return shouldSkip(subject, EntityFeatureSchema.SubjectKind.ENTITY, bounds, flags, logicTick);
    }

    public boolean shouldSkipBlockEntity(Object subject, AABBf bounds, int flags, int logicTick) {
        return shouldSkip(subject, EntityFeatureSchema.SubjectKind.BLOCK_ENTITY, bounds, flags, logicTick);
    }

    public int subjectCount() {
        return stateStore.subjectCount(sourceRegistry);
    }

    public int dispatchGroupCount() {
        return stateStore.dispatchGroupCount(sourceRegistry);
    }

    public boolean isActive() {
        return stateStore.isAllocated();
    }

    private boolean shouldSkip(
            Object subject,
            EntityFeatureSchema.SubjectKind subjectKind,
            AABBf bounds,
            int flags,
            int logicTick) {
        if (subject == null || bounds == null || !stateStore.isAllocated()) {
            return false;
        }
        return !stateStore.isVisible(subject, sourceRegistry);
    }
}
