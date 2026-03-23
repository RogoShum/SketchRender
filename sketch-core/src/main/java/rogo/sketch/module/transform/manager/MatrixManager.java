package rogo.sketch.module.transform.manager;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.kernel.KernelResourceKey;
import rogo.sketch.core.pipeline.kernel.PipelineKernel;
import rogo.sketch.core.pipeline.kernel.PublishedKernelResource;

/**
 * Transform system facade coordinating registration, CPU state, hierarchy, snapshots, and GPU upload.
 */
public class MatrixManager {
    public static final KernelResourceKey<TransformPreparedTickSnapshot> TICK_SNAPSHOT_RESOURCE =
            KernelResourceKey.of("transform.tick_snapshot", TransformPreparedTickSnapshot.class);

    private final TransformRegistry registry = new TransformRegistry();
    private final TransformStateStore stateStore = new TransformStateStore();
    private final TransformHierarchyGraph hierarchyGraph = new TransformHierarchyGraph();
    private final TransformSnapshotBuilder snapshotBuilder = new TransformSnapshotBuilder();
    private final TransformPipeline syncPipeline = new TransformPipeline(64);
    private final TransformPipeline asyncPipeline = new TransformPipeline(1024);
    private final TransformMatrixOutputBuffer outputBuffer = new TransformMatrixOutputBuffer();
    private TransformPreparedTickSnapshot activeTickSnapshot;

    public TransformBinding registerBinding(Graphics graphics, TransformUpdateDomain updateDomain) {
        TransformBinding binding = registry.registerBinding(graphics, updateDomain);
        stateStore.initializeBinding(binding);
        hierarchyGraph.markDirty();
        outputBuffer.ensureCapacityForMaxId(registry.maxAllocatedId());
        return binding;
    }

    public void unregisterBinding(TransformBinding binding) {
        registry.unregisterBinding(binding);
        hierarchyGraph.markDirty();
    }

    public TransformBinding bindingFor(Graphics graphics) {
        return registry.bindingFor(graphics);
    }

    public boolean isRegistered(Graphics graphics) {
        return registry.isRegistered(graphics);
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

    public void prepareAndPublishTickSnapshot(PipelineKernel<?> kernel, long logicTickEpoch) {
        hierarchyGraph.resolveIfNeeded(registry);
        outputBuffer.ensureCapacityForMaxId(registry.maxAllocatedId());
        TransformPreparedTickSnapshot snapshot =
                snapshotBuilder.buildTickSnapshot(logicTickEpoch, registry, hierarchyGraph);
        kernel.publishResource(TICK_SNAPSHOT_RESOURCE, logicTickEpoch, snapshot);
    }

    public void prepareFrameBuffer(PipelineKernel<?> kernel) {
        PublishedKernelResource<TransformPreparedTickSnapshot> published =
                kernel.peekResource(TICK_SNAPSHOT_RESOURCE);

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

    public void uploadFrameBuffers() {
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