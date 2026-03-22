package rogo.sketch.core.pipeline.kernel;

import org.lwjgl.glfw.GLFW;
import rogo.sketch.core.driver.GLRuntimeFlags;
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
import rogo.sketch.core.pipeline.module.ModuleRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Central orchestrator for the RenderGraph + TaskGraph pipeline.
 * <p>
 * Thread model:
 * <ul>
 *   <li><b>Sketch-TickTask-Worker</b> -- pure CPU, no GL context</li>
 *   <li><b>Sketch-TickGL-Worker</b> -- dedicated shared GL context for tick-owned transform async work</li>
 *   <li><b>Sketch-RenderTask-Worker</b> -- shared GL context (if {@link GLRuntimeFlags#GL_WORKER_ENABLED})</li>
 * </ul>
 * <p>
 * Pass structure (3 passes):
 * <pre>
 *   SyncCommitPass  (no deps)          -- consume N-1 BuildResult, VAO materialize, command queue commit
 *   SyncPreparePass (after Commit)     -- dirty collect, prepare frame, reset write buffer
 *   AsyncRenderPass (after Prepare)    -- shader compile, VBO upload (if worker GL), command build
 * </pre>
 *
 * @param <C> Concrete RenderContext type
 */
public class PipelineKernel<C extends RenderContext> {
    private final GraphicsPipeline<C> pipeline;
    private final ModuleRegistry moduleRegistry;
    private final ExecutorService tickExecutor;
    private final ExecutorService tickGlExecutor;
    private final ExecutorService frameExecutor;
    private final TaskGraphScheduler tickScheduler;
    private final TaskGraphScheduler tickGlScheduler;
    private final TaskGraphScheduler frameScheduler;
    private final PendingBuildSlot pendingBuildSlot = new PendingBuildSlot();

    private CompiledTickGraph<C> compiledTickGraph;
    private CompiledRenderGraph<C> compiledFrameGraph;
    private long frameNumber = 0;

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
        this.tickScheduler = new TaskGraphScheduler(tickExecutor, WorkerContextMode.NONE, true);
        this.tickGlScheduler = new TaskGraphScheduler(tickGlExecutor, WorkerContextMode.TICK_GL, true);
        this.frameScheduler = new TaskGraphScheduler(frameExecutor, WorkerContextMode.RENDER_GL, true);
    }

    /**
     * Initialize the kernel: register main thread, init worker context, compile graph.
     * Must be called from the main thread after pipeline initialization.
     */
    public void initialize() {
        ThreadDomainGuard.registerMainThread();

        if (GLRuntimeFlags.GL_WORKER_ENABLED) {
            long mainWindow = GLFW.glfwGetCurrentContext();
            GraphicsDriver.getCurrentAPI().initRenderWorkerContext(mainWindow);
            GraphicsDriver.getCurrentAPI().initTickWorkerContext(mainWindow);
        }

        moduleRegistry.initialize(pipeline);
        rebuildGraphs();
    }

    /**
     * Rebuild the tick and frame graphs from the current module set.
     * Tick graph prepares interpolation data at tick boundaries, while the
     * frame graph remains a staged sync-front / async-tail render pipeline.
     */
    public void rebuildGraphs() {
        TickGraphBuilder<C> tickBuilder = new TickGraphBuilder<>(pipeline);
        tickBuilder
                .addPostTickPass(new PostTickGraphicsPass<>())
                .addPostTickPass(new PostTickAsyncGraphicsPass<>(), PostTickGraphicsPass.NAME);

        moduleRegistry.contributeToTickGraph(tickBuilder);
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

        moduleRegistry.contributeToFrameGraph(frameBuilder);
        frameBuilder.addPass(new AsyncRenderPass<>(), SyncPreparePass.NAME);

        this.compiledFrameGraph = frameBuilder.compile();
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
            // Cross-frame: async pass publishes result to PendingBuildSlot;
            // waitAtFrameEnd=false so we don't block on the current frame's async build.
            frameScheduler.execute(compiledFrameGraph, frameCtx, false);
        }
    }

    // ==================== Accessors ====================

    public GraphicsPipeline<C> pipeline() { return pipeline; }
    public ModuleRegistry moduleRegistry() { return moduleRegistry; }
    public TaskGraphScheduler tickScheduler() { return tickScheduler; }
    public TaskGraphScheduler tickGlScheduler() { return tickGlScheduler; }
    public TaskGraphScheduler frameScheduler() { return frameScheduler; }

    public boolean isGraphCompiled() {
        return compiledTickGraph != null && compiledFrameGraph != null;
    }

    public void publishBuildResult(BuildResult buildResult) {
        if (buildResult != null) {
            pendingBuildSlot.publish(buildResult);
        }
    }

    public BuildResult consumeBuildResult() {
        return pendingBuildSlot.consume();
    }

    public void cleanup() {
        moduleRegistry.cleanup();
        tickScheduler.shutdown();
        tickGlScheduler.shutdown();
        frameScheduler.shutdown();

        if (GLRuntimeFlags.GL_WORKER_ENABLED) {
            GraphicsDriver.getCurrentAPI().destroyTickWorkerContext();
            GraphicsDriver.getCurrentAPI().destroyRenderWorkerContext();
        }
    }
}
