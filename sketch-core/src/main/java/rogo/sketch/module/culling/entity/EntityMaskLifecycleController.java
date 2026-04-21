package rogo.sketch.module.culling.entity;

import rogo.sketch.core.pipeline.RenderContext;

/**
 * Aligns entity mask lifetime with module/world/settings boundaries.
 */
public final class EntityMaskLifecycleController {
    private final EntitySourceRegistry sourceRegistry;
    private final EntityMaskStateStore stateStore;

    private boolean worldActive;
    private boolean runtimeEnabled;
    private boolean entityCullingEnabled;
    private boolean blockEntityCullingEnabled;

    public EntityMaskLifecycleController(
            EntitySourceRegistry sourceRegistry,
            EntityMaskStateStore stateStore,
            rogo.sketch.module.culling.VisibilitySystem visibilitySystem) {
        this.sourceRegistry = sourceRegistry;
        this.stateStore = stateStore;
    }

    public void onWorldEnter() {
        worldActive = true;
        syncAllocation();
    }

    public void onWorldLeave() {
        worldActive = false;
        disposeAll();
    }

    public void onEnable() {
        runtimeEnabled = true;
        syncAllocation();
    }

    public void onDisable() {
        runtimeEnabled = false;
        disposeAll();
    }

    public void syncSettings(boolean entityCullingEnabled, boolean blockEntityCullingEnabled) {
        this.entityCullingEnabled = entityCullingEnabled;
        this.blockEntityCullingEnabled = blockEntityCullingEnabled;
        syncAllocation();
    }

    public void beginFrame(int logicTick, boolean nextLoop) {
        if (isActive() && nextLoop) {
            stateStore.swapBuffers(logicTick, sourceRegistry);
        }
    }

    public void refreshEntityInputs(RenderContext renderContext, boolean nextLoop) {
        if (isActive() && nextLoop) {
            stateStore.refreshEntityInputs(sourceRegistry);
        }
    }

    public void shutdown() {
        disposeAll();
        runtimeEnabled = false;
        worldActive = false;
        entityCullingEnabled = false;
        blockEntityCullingEnabled = false;
    }

    public boolean isActive() {
        return runtimeEnabled && worldActive && (entityCullingEnabled || blockEntityCullingEnabled);
    }

    public int subjectCount() {
        return stateStore.subjectCount(sourceRegistry);
    }

    public int dispatchGroupCount() {
        return stateStore.dispatchGroupCount(sourceRegistry);
    }

    public EntitySourceRegistry sourceRegistry() {
        return sourceRegistry;
    }

    public EntityMaskStateStore stateStore() {
        return stateStore;
    }

    private void syncAllocation() {
        if (isActive()) {
            stateStore.ensureAllocated(Math.max(EntityFeatureSchema.DEFAULT_INITIAL_CAPACITY, sourceRegistry.subjectCount()));
        } else {
            disposeAll();
        }
    }

    private void disposeAll() {
        stateStore.dispose();
        sourceRegistry.clear();
    }
}
