package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.pass.AsyncRenderPass;
import rogo.sketch.core.pipeline.graph.pass.SyncApplyPendingSettingsPass;
import rogo.sketch.core.pipeline.graph.pass.SyncCommitPass;
import rogo.sketch.core.pipeline.graph.pass.SyncPreparePass;
import rogo.sketch.core.pipeline.graph.scheduler.TaskGraphScheduler;
import rogo.sketch.core.pipeline.kernel.commit.FrameCommitPipeline;
import rogo.sketch.core.pipeline.module.ModuleRegistry;
import rogo.sketch.core.util.KeyId;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Central orchestrator for the RenderGraph + TaskGraph pipeline.
 * <p>
 * Thread model:
 * <ul>
 *   <li><b>Sketch-TickTask-Worker</b> -- pure CPU, no GL context</li>
 *   <li><b>Sketch-TickTask-Worker</b> -- backend tick async lane</li>
 *   <li><b>Sketch-FrameTask-Worker</b> -- backend render async lane</li>
 *   <li><b>Sketch-GpuCompute-Worker</b> -- backend compute async lane</li>
 *   <li><b>Sketch-GpuUpload-Worker</b> -- backend upload async lane</li>
 *   <li><b>Sketch-GpuGraphics-Worker</b> -- backend offscreen graphics async lane</li>
 * </ul>
 * <p>
 * Pass structure (3 passes):
 * <pre>
 *   SyncCommitPass          (no deps)     -- consume N-1 BuildResult, VAO materialize, command queue commit
 *   SyncPreparePass         (after Commit)-- dirty collect, prepare frame, reset write buffer
 *   SyncApplySettingsPass   (after Prepare)-- flush pending UI/config changes into committed runtime state
 *   AsyncRenderPass         (after Apply) -- packet build and optional backend worker uploads
 * </pre>
 *
 * @param <C> Concrete RenderContext type
 */
public class PipelineKernel<C extends RenderContext> {
    public static final FrameResourceHandle<BuildResult> BUILD_RESULT_HANDLE =
            FrameResourceHandle.of(
                    KeyId.of("sketch_render", "build_result"),
                    BuildResult.class,
                    "pipeline",
                    "pipeline.build_result");

    private final GraphicsPipeline<C> pipeline;
    private final ModuleRegistry moduleRegistry;
    private final WorkerCoordinator workerCoordinator;
    private final FrameScheduler<C> frameScheduler;
    private final ConcurrentHashMap<FrameResourceHandle<?>, FrameResourceSlot<?>> frameResources = new ConcurrentHashMap<>();
    private final FrameCommitPipeline<C> commitPipeline;
    private long frameNumber = 0;

    public PipelineKernel(GraphicsPipeline<C> pipeline) {
        this.pipeline = pipeline;
        this.moduleRegistry = new ModuleRegistry();
        this.workerCoordinator = new WorkerCoordinator();
        this.frameScheduler = new FrameScheduler<>(pipeline, moduleRegistry, workerCoordinator);
        this.commitPipeline = new FrameCommitPipeline<>();
    }

    /**
     * Initialize the kernel: register main thread, init backend worker lanes, compile graph.
     * Must be called from the main thread after pipeline initialization.
     */
    public void initialize() {
        ThreadDomainGuard.registerMainThread();
        workerCoordinator.initializeBackendWorkerLanes();
        moduleRegistry.initializeKernel(this);
        rebuildGraphs();
    }

    /**
     * Rebuild the tick and frame graphs from the current module set.
     * Tick graph prepares interpolation data at tick boundaries, while the
     * frame graph remains a staged sync-front / async-tail render pipeline.
     */
    public void rebuildGraphs() {
        frameScheduler.rebuildGraphs(frameNumber);
    }

    // ==================== Tick Lifecycle ====================

    /**
     * PRE TICK: wait for the previous tick's async collection, prepare the
     * interpolation builders that render will upload later, then rotate per-tick buffers.
     */
    public void onPreTick() {
        frameScheduler.onPreTick(this, pipeline.currentContext(), pipeline.currentLogicTick());
    }

    /**
     * POST TICK: after the game tick updates world state, collect sync transform
     * data and enqueue async transform collection for the remaining tick budget.
     */
    public void onPostTick() {
        frameScheduler.onPostTick(this, pipeline.currentContext(), pipeline.currentLogicTick());
    }

    // ==================== Render Lifecycle ====================

    /**
     * Execute the full render graph for the current frame.
     * <p>
     * Cross-frame model: consume previous frame's BuildResult, then submit
     * current frame's async build without blocking.
     */
    public void executeFrame(C renderContext) {
        frameScheduler.executeFrame(this, renderContext, frameNumber++);
    }

    // ==================== Accessors ====================

    public GraphicsPipeline<C> pipeline() { return pipeline; }
    public ModuleRegistry moduleRegistry() { return moduleRegistry; }
    public FrameCommitPipeline<C> commitPipeline() { return commitPipeline; }
    public TaskGraphScheduler tickScheduler() { return workerCoordinator.tickScheduler(); }
    public TaskGraphScheduler tickGlScheduler() { return workerCoordinator.tickGlScheduler(); }
    public TaskGraphScheduler frameScheduler() { return workerCoordinator.frameScheduler(); }
    public rogo.sketch.core.backend.AsyncGpuScheduler asyncGpuScheduler() { return workerCoordinator.asyncGpuScheduler(); }
    public GraphSnapshot graphSnapshot() { return frameScheduler.graphSnapshot(); }

    public boolean isGraphCompiled() {
        return frameScheduler.isGraphCompiled();
    }

    public void publishBuildResult(BuildResult buildResult) {
        if (buildResult != null) {
            publishFrameResource(BUILD_RESULT_HANDLE, buildResult.resourceEpoch(), buildResult);
        }
    }

    public BuildResult consumeBuildResult() {
        PublishedFrameResource<BuildResult> frameResource = consumeFrameResource(BUILD_RESULT_HANDLE);
        return frameResource != null ? frameResource.payload() : null;
    }

    public LifecyclePhase phaseForPass(String moduleId, String passId) {
        return frameScheduler.phaseForPass(moduleId, passId);
    }

    public PassExecutionContext passExecutionContext(
            String moduleId,
            String passId,
            long frameEpoch,
            long logicTickEpoch) {
        return frameScheduler.passExecutionContext(this, moduleId, passId, frameEpoch, logicTickEpoch);
    }

    public <T> PublishedFrameResource<T> publishFrameResource(FrameResourceHandle<T> handle, long epoch, T payload) {
        return publishFrameResourceInternal(handle, epoch, payload);
    }

    public <T> PublishedFrameResource<T> peekFrameResource(FrameResourceHandle<T> handle) {
        return slot(handle).peek();
    }

    public <T> PublishedFrameResource<T> consumeFrameResource(FrameResourceHandle<T> handle) {
        return slot(handle).consume();
    }

    private <T> PublishedFrameResource<T> publishFrameResourceInternal(
            FrameResourceHandle<T> handle,
            long epoch,
            T payload) {
        return slot(handle).publish(handle, epoch, payload);
    }

    @SuppressWarnings("unchecked")
    private <T> FrameResourceSlot<T> slot(FrameResourceHandle<T> handle) {
        return (FrameResourceSlot<T>) frameResources.computeIfAbsent(handle, ignored -> new FrameResourceSlot<>());
    }

    public void cleanup() {
        moduleRegistry.cleanup();
        workerCoordinator.shutdown();
    }

    private static final class FrameResourceSlot<T> {
        private final AtomicReference<PublishedFrameResource<T>> latest = new AtomicReference<>();
        private final AtomicLong sequence = new AtomicLong();

        PublishedFrameResource<T> publish(FrameResourceHandle<T> handle, long epoch, T payload) {
            PublishedFrameResource<T> published =
                    new PublishedFrameResource<>(handle, payload, epoch, sequence.incrementAndGet());
            latest.set(published);
            return published;
        }

        PublishedFrameResource<T> peek() {
            return latest.get();
        }

        PublishedFrameResource<T> consume() {
            return latest.getAndSet(null);
        }
    }
}
