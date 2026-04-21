package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.backend.BackendWorkerLane;
import rogo.sketch.core.backend.AsyncGpuScheduler;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.CompiledTickGraph;
import rogo.sketch.core.pipeline.graph.CompiledRenderGraph;
import rogo.sketch.core.pipeline.graph.RenderGraphBuilder;
import rogo.sketch.core.pipeline.graph.TickGraphBuilder;
import rogo.sketch.core.pipeline.graph.pass.*;
import rogo.sketch.core.pipeline.graph.scheduler.TaskGraphScheduler;
import rogo.sketch.core.pipeline.graph.scheduler.TaskGraphScheduler.WorkerContextMode;
import rogo.sketch.core.pipeline.kernel.commit.FrameCommitPipeline;
import rogo.sketch.core.pipeline.module.ModuleRegistry;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeHost;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 *   <li><b>Sketch-RenderTask-Worker</b> -- backend render async lane</li>
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
    private final ExecutorService tickExecutor;
    private final ExecutorService tickGlExecutor;
    private final ExecutorService frameExecutor;
    private final ExecutorService gpuComputeExecutor;
    private final ExecutorService gpuUploadExecutor;
    private final ExecutorService gpuGraphicsExecutor;
    private final TaskGraphScheduler tickScheduler;
    private final TaskGraphScheduler tickGlScheduler;
    private final TaskGraphScheduler frameScheduler;
    private final AsyncGpuScheduler asyncGpuScheduler;
    private final ConcurrentHashMap<FrameResourceHandle<?>, FrameResourceSlot<?>> frameResources = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LifecyclePhase> passPhaseIndex = new ConcurrentHashMap<>();
    private final AtomicLong graphVersion = new AtomicLong();
    private final FrameCommitPipeline<C> commitPipeline;

    private CompiledTickGraph<C> compiledTickGraph;
    private CompiledRenderGraph<C> compiledFrameGraph;
    private long frameNumber = 0;
    private volatile GraphSnapshot graphSnapshot = GraphSnapshot.empty();

    public PipelineKernel(GraphicsPipeline<C> pipeline) {
        this.pipeline = pipeline;
        this.moduleRegistry = new ModuleRegistry();
        this.tickExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Sketch-TickTask-Worker");
            t.setDaemon(true);
            return t;
        });
        this.tickGlExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Sketch-TickGL-Worker");
            t.setDaemon(true);
            return t;
        });
        this.frameExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Sketch-RenderTask-Worker");
            t.setDaemon(true);
            return t;
        });
        this.gpuComputeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Sketch-GpuCompute-Worker");
            t.setDaemon(true);
            return t;
        });
        this.gpuUploadExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Sketch-GpuUpload-Worker");
            t.setDaemon(true);
            return t;
        });
        this.gpuGraphicsExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Sketch-GpuGraphics-Worker");
            t.setDaemon(true);
            return t;
        });
        this.tickScheduler = new TaskGraphScheduler(tickExecutor, WorkerContextMode.NONE, true);
        this.tickGlScheduler = new TaskGraphScheduler(tickGlExecutor, WorkerContextMode.TICK_ASYNC, true);
        this.frameScheduler = new TaskGraphScheduler(frameExecutor, WorkerContextMode.RENDER_ASYNC, true);
        this.asyncGpuScheduler = new AsyncGpuScheduler(gpuComputeExecutor, gpuUploadExecutor, gpuGraphicsExecutor, true);
        this.commitPipeline = new FrameCommitPipeline<>();
    }

    /**
     * Initialize the kernel: register main thread, init backend worker lanes, compile graph.
     * Must be called from the main thread after pipeline initialization.
     */
    public void initialize() {
        ThreadDomainGuard.registerMainThread();

        if (GraphicsDriver.capabilities().workerLanesSupported()) {
            GraphicsDriver.runtime().initializeWorkerLane(BackendWorkerLane.RENDER_ASYNC);
            GraphicsDriver.runtime().initializeWorkerLane(BackendWorkerLane.TICK_ASYNC);
            if (GraphicsDriver.capabilities().supportsLane(BackendWorkerLane.COMPUTE_ASYNC)) {
                GraphicsDriver.runtime().initializeWorkerLane(BackendWorkerLane.COMPUTE_ASYNC);
            }
            if (GraphicsDriver.capabilities().supportsLane(BackendWorkerLane.UPLOAD_ASYNC)) {
                GraphicsDriver.runtime().initializeWorkerLane(BackendWorkerLane.UPLOAD_ASYNC);
            }
            if (GraphicsDriver.capabilities().supportsLane(BackendWorkerLane.OFFSCREEN_GRAPHICS_ASYNC)) {
                GraphicsDriver.runtime().initializeWorkerLane(BackendWorkerLane.OFFSCREEN_GRAPHICS_ASYNC);
            }
        }

        moduleRegistry.initializeKernel(this);
        rebuildGraphs();
    }

    /**
     * Rebuild the tick and frame graphs from the current module set.
     * Tick graph prepares interpolation data at tick boundaries, while the
     * frame graph remains a staged sync-front / async-tail render pipeline.
     */
    public void rebuildGraphs() {
        rebuildGraphSnapshot();

        TickGraphBuilder<C> tickBuilder = new TickGraphBuilder<>(pipeline);
        tickBuilder
                .addPostTickPass(new PostTickGraphicsPass<>());

        moduleRegistry.contributeToTickGraph(tickBuilder);
        List<String> postTickAsyncDependencies = new ArrayList<>();
        if (tickBuilder.hasPostTickGlAsyncPass("transform_async_tick_collect")) {
            postTickAsyncDependencies.add("transform_async_tick_collect");
        }
        if (tickBuilder.hasPostTickGlAsyncPass("culling_async_prepare_entity_subjects")) {
            postTickAsyncDependencies.add("culling_async_prepare_entity_subjects");
        }
        tickBuilder.addPostTickGlAsyncPass(new PostTickAsyncGraphicsPass<>(),
                postTickAsyncDependencies.toArray(String[]::new));
        if (tickBuilder.hasPreTickPass("transform_tick_swap")) {
            tickBuilder.addPreTickPass(new PreTickSwapDataPass<>(), "transform_tick_swap");
        } else {
            tickBuilder.addPreTickPass(new PreTickSwapDataPass<>());
        }
        this.compiledTickGraph = tickBuilder.compile();

        RenderGraphBuilder<C> frameBuilder = new RenderGraphBuilder<>(pipeline);

        // Core pipeline passes: 3-pass cross-frame pipeline
        frameBuilder
                .addPass(new SyncCommitPass<>())
                .addPass(new SyncPreparePass<>(), SyncCommitPass.NAME);
        frameBuilder.addPass(new SyncApplyPendingSettingsPass<>(), SyncPreparePass.NAME);

        moduleRegistry.contributeToFrameGraph(frameBuilder);
        frameBuilder.addPass(new AsyncRenderPass<>(), SyncApplyPendingSettingsPass.NAME);

        this.compiledFrameGraph = frameBuilder.compile();
    }

    private void rebuildGraphSnapshot() {
        ModuleRuntimeHost runtimeHost = moduleRegistry.runtimeHost();
        Map<String, List<ModulePassDefinition>> modulePasses = Map.of();
        Map<KeyId, FrameResourceHandle<?>> resourceHandles = new LinkedHashMap<>();

        if (runtimeHost != null) {
            GraphSnapshot assembled = runtimeHost.assembleGraphSnapshot(
                    graphVersion.get() + 1L,
                    frameNumber);
            modulePasses = assembled.modulePasses();
            resourceHandles.putAll(assembled.resourceHandles());
        }

        resourceHandles.put(BUILD_RESULT_HANDLE.id(), BUILD_RESULT_HANDLE);

        GraphSnapshot snapshot = new GraphSnapshot(
                graphVersion.incrementAndGet(),
                frameNumber,
                modulePasses,
                resourceHandles);
        graphSnapshot = snapshot;
        installGraphSnapshot(snapshot);
    }

    private void installGraphSnapshot(GraphSnapshot snapshot) {
        passPhaseIndex.clear();

        registerCorePhase(SyncCommitPass.NAME, LifecyclePhase.SYNC_COMMIT);
        registerCorePhase(SyncPreparePass.NAME, LifecyclePhase.SYNC_PREPARE);
        registerCorePhase(SyncApplyPendingSettingsPass.NAME, LifecyclePhase.SYNC_PRE_BUILD);
        registerCorePhase(AsyncRenderPass.NAME, LifecyclePhase.ASYNC_BUILD);

        for (List<ModulePassDefinition> definitions : snapshot.modulePasses().values()) {
            for (ModulePassDefinition definition : definitions) {
                registerPhase(definition.moduleId(), definition.passId(), definition.phase());
            }
        }
    }

    private void registerCorePhase(String passId, LifecyclePhase phase) {
        passPhaseIndex.put(passId, phase);
    }

    private void registerPhase(String moduleId, String passId, LifecyclePhase phase) {
        passPhaseIndex.put(moduleId + ":" + passId, phase);
        passPhaseIndex.putIfAbsent(passId, phase);
    }

    // ==================== Tick Lifecycle ====================

    /**
     * PRE TICK: wait for the previous tick's async collection, prepare the
     * interpolation builders that render will upload later, then rotate per-tick buffers.
     */
    public void onPreTick() {
        if (compiledTickGraph == null) {
            return;
        }
        TickContext<C> tickCtx = new TickContext<>(pipeline, this, pipeline.currentContext(), pipeline.currentLogicTick());
        tickScheduler.awaitPendingAsync();
        tickGlScheduler.awaitPendingAsync();
        tickScheduler.execute(compiledTickGraph.preTickGraph(), tickCtx, false);
    }

    /**
     * POST TICK: after the game tick updates world state, collect sync transform
     * data and enqueue async transform collection for the remaining tick budget.
     */
    public void onPostTick() {
        if (compiledTickGraph == null) {
            return;
        }
        TickContext<C> tickCtx = new TickContext<>(pipeline, this, pipeline.currentContext(), pipeline.currentLogicTick());
        tickScheduler.execute(compiledTickGraph.postTickGraph(), tickCtx, false);
        if (compiledTickGraph.postTickGlAsyncGraph().passCount() > 0) {
            tickGlScheduler.execute(compiledTickGraph.postTickGlAsyncGraph(), tickCtx, false);
        }
    }

    // ==================== Render Lifecycle ====================

    /**
     * Execute the full render graph for the current frame.
     * <p>
     * Cross-frame model: consume previous frame's BuildResult, then submit
     * current frame's async build without blocking.
     */
    public void executeFrame(C renderContext) {
        FrameContext<C> frameCtx = new FrameContext<>(
                pipeline, this, renderContext, frameNumber++);

        if (compiledFrameGraph != null) {
            // Cross-frame: async pass publishes result to the kernel resource bus;
            // waitAtFrameEnd=false so we don't block on the current frame's async build.
            frameScheduler.execute(compiledFrameGraph, frameCtx, false);
        }
    }

    // ==================== Accessors ====================

    public GraphicsPipeline<C> pipeline() { return pipeline; }
    public ModuleRegistry moduleRegistry() { return moduleRegistry; }
    public FrameCommitPipeline<C> commitPipeline() { return commitPipeline; }
    public TaskGraphScheduler tickScheduler() { return tickScheduler; }
    public TaskGraphScheduler tickGlScheduler() { return tickGlScheduler; }
    public TaskGraphScheduler frameScheduler() { return frameScheduler; }
    public AsyncGpuScheduler asyncGpuScheduler() { return asyncGpuScheduler; }
    public GraphSnapshot graphSnapshot() { return graphSnapshot; }

    public boolean isGraphCompiled() {
        return compiledTickGraph != null && compiledFrameGraph != null;
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
        if (moduleId != null && passId != null) {
            LifecyclePhase qualified = passPhaseIndex.get(moduleId + ":" + passId);
            if (qualified != null) {
                return qualified;
            }
        }
        return passId != null ? passPhaseIndex.get(passId) : null;
    }

    public PassExecutionContext passExecutionContext(
            String moduleId,
            String passId,
            long frameEpoch,
            long logicTickEpoch) {
        LifecyclePhase phase = phaseForPass(moduleId, passId);
        if (phase == null) {
            phase = LifecyclePhase.SYNC_PRE_BUILD;
        }
        return new PassExecutionContext(this, moduleId, passId, phase, frameEpoch, logicTickEpoch);
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
        tickScheduler.shutdown();
        tickGlScheduler.shutdown();
        frameScheduler.shutdown();
        asyncGpuScheduler.shutdown();

        if (GraphicsDriver.capabilities().workerLanesSupported()) {
            if (GraphicsDriver.capabilities().supportsLane(BackendWorkerLane.OFFSCREEN_GRAPHICS_ASYNC)) {
                GraphicsDriver.runtime().destroyWorkerLane(BackendWorkerLane.OFFSCREEN_GRAPHICS_ASYNC);
            }
            if (GraphicsDriver.capabilities().supportsLane(BackendWorkerLane.UPLOAD_ASYNC)) {
                GraphicsDriver.runtime().destroyWorkerLane(BackendWorkerLane.UPLOAD_ASYNC);
            }
            if (GraphicsDriver.capabilities().supportsLane(BackendWorkerLane.COMPUTE_ASYNC)) {
                GraphicsDriver.runtime().destroyWorkerLane(BackendWorkerLane.COMPUTE_ASYNC);
            }
            GraphicsDriver.runtime().destroyWorkerLane(BackendWorkerLane.TICK_ASYNC);
            GraphicsDriver.runtime().destroyWorkerLane(BackendWorkerLane.RENDER_ASYNC);
        }
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
