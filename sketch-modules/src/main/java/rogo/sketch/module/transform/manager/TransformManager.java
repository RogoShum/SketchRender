package rogo.sketch.module.transform.manager;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsWorld;
import rogo.sketch.core.pipeline.kernel.FrameResourceHandle;
import rogo.sketch.core.pipeline.kernel.PassExecutionContext;
import rogo.sketch.core.pipeline.kernel.PublishedFrameResource;
import rogo.sketch.core.util.KeyId;

/**
 * Transform system facade coordinating registration, CPU state, hierarchy, snapshots, and GPU upload.
 */
public class TransformManager {
    public static final FrameResourceHandle<TransformPreparedTickSnapshot> TICK_SNAPSHOT_HANDLE =
            FrameResourceHandle.of(
                    KeyId.of("sketch_render", "transform_tick_snapshot"),
                    TransformPreparedTickSnapshot.class,
                    "transform",
                    "transform.tick_snapshot");

    private final TransformRegistry registry = new TransformRegistry();
    private final TransformStateStore stateStore = new TransformStateStore();
    private final TransformHierarchyGraph hierarchyGraph = new TransformHierarchyGraph();
    private final TransformSnapshotBuilder snapshotBuilder = new TransformSnapshotBuilder();
    private final TransformPipeline syncPipeline = new TransformPipeline(64);
    private final TransformPipeline asyncPipeline = new TransformPipeline(1024);
    private final TransformMatrixOutputBuffer outputBuffer = new TransformMatrixOutputBuffer();
    private TransformPreparedTickSnapshot activeTickSnapshot;

    public TransformBinding registerBinding(
            GraphicsWorld world,
            GraphicsEntityId entityId,
            GraphicsBuiltinComponents.TransformBindingComponent bindingComponent,
            GraphicsBuiltinComponents.TransformHierarchyComponent hierarchyComponent) {
        TransformBinding binding = registry.registerBinding(entityId, bindingComponent, hierarchyComponent);
        stateStore.initializeBinding(binding);
        hierarchyGraph.markDirty();
        outputBuffer.ensureCapacityForMaxId(registry.maxAllocatedId());
        if (world != null && bindingComponent != null) {
            world.replaceComponent(entityId, GraphicsBuiltinComponents.TRANSFORM_BINDING,
                    new GraphicsBuiltinComponents.TransformBindingComponent(
                            bindingComponent.updateDomain(),
                            bindingComponent.authoring(),
                            binding.transformId()));
        }
        return binding;
    }

    public void unregisterBinding(TransformBinding binding) {
        registry.unregisterBinding(binding);
        hierarchyGraph.markDirty();
    }

    public TransformBinding bindingFor(GraphicsEntityId entityId) {
        return registry.bindingFor(entityId);
    }

    public boolean isRegistered(GraphicsEntityId entityId) {
        return registry.isRegistered(entityId);
    }

    public TransformBinding bindingById(int id) {
        return registry.bindingById(id);
    }

    public int getActiveCount() {
        return registry.activeCount();
    }

    public void swapTickBuffers() {
        stateStore.swapTickBuffers(registry);
    }

    public void collectSyncTickTransforms() {
        stateStore.collectSyncTickTransforms(registry);
    }

    public void collectAsyncTickTransforms() {
        stateStore.collectAsyncTickTransforms(registry);
    }

    public void collectFrameTransforms() {
        stateStore.collectFrameTransforms(registry);
    }

    public void prepareAndPublishTickSnapshot(PassExecutionContext passExecutionContext) {
        hierarchyGraph.resolveIfNeeded(registry);
        outputBuffer.ensureCapacityForMaxId(registry.maxAllocatedId());
        long logicTickEpoch = passExecutionContext != null ? passExecutionContext.logicTickEpoch() : -1L;
        TransformPreparedTickSnapshot snapshot =
                snapshotBuilder.buildTickSnapshot(logicTickEpoch, registry, hierarchyGraph);
        if (passExecutionContext != null) {
            passExecutionContext.publish(TICK_SNAPSHOT_HANDLE, logicTickEpoch, snapshot);
        }
    }

    public void prepareFrameBuffer(PassExecutionContext passExecutionContext) {
        PublishedFrameResource<TransformPreparedTickSnapshot> published =
                passExecutionContext != null ? passExecutionContext.peek(TICK_SNAPSHOT_HANDLE) : null;

        if (published != null) {
            activeTickSnapshot = published.payload();
        } else if (activeTickSnapshot == null) {
            hierarchyGraph.resolveIfNeeded(registry);
            activeTickSnapshot = snapshotBuilder.buildTickSnapshot(-1L, registry, hierarchyGraph);
        }

        if (activeTickSnapshot == null) {
            return;
        }

        syncPipeline.loadSnapshot(activeTickSnapshot.syncSnapshot());
        asyncPipeline.loadSnapshot(activeTickSnapshot.asyncSnapshot());
        syncPipeline.applyFrameOverrides(registry.frameBindings(), activeTickSnapshot.syncSnapshot());
    }

    public void uploadFrameBuffers(PassExecutionContext passExecutionContext) {
        if (passExecutionContext != null) {
            passExecutionContext.peek(TICK_SNAPSHOT_HANDLE);
        }
        syncPipeline.upload();
        asyncPipeline.upload();
    }

    public ResourceObject getOutputSSBO() {
        return outputBuffer.resource();
    }

    public TransformPipeline getSyncPipeline() {
        return syncPipeline;
    }

    public TransformPipeline getAsyncPipeline() {
        return asyncPipeline;
    }

    public void cleanup() {
        registry.clear();
        syncPipeline.cleanup();
        asyncPipeline.cleanup();
        outputBuffer.cleanup();
        snapshotBuilder.cleanup();
    }
}
