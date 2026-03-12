package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.driver.GLRuntimeFlags;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.CompiledRenderGraph;
import rogo.sketch.core.pipeline.graph.RenderGraphBuilder;
import rogo.sketch.core.pipeline.graph.pass.*;
import rogo.sketch.core.pipeline.graph.scheduler.TaskGraphScheduler;
import rogo.sketch.core.pipeline.module.ModuleRegistry;
import rogo.sketch.core.util.TimerUtil;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Central orchestrator for the RenderGraph + TaskGraph pipeline.
 * <p>
 * Thread model:
 * <ul>
 *   <li><b>Sketch-TickTask-Worker</b> -- pure CPU, no GL context</li>
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
    private static final ExecutorService TICK_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Sketch-TickTask-Worker");
        t.setDaemon(true);
        return t;
    });

    private final GraphicsPipeline<C> pipeline;
    private final ModuleRegistry moduleRegistry;
    private final TaskGraphScheduler scheduler;
    private final PendingBuildSlot pendingBuildSlot = new PendingBuildSlot();

    private CompiledRenderGraph<C> compiledGraph;
    private long frameNumber = 0;
    private CompletableFuture<Void> asyncTickTask = CompletableFuture.completedFuture(null);

    public PipelineKernel(GraphicsPipeline<C> pipeline) {
        this.pipeline = pipeline;
        this.moduleRegistry = new ModuleRegistry();
        this.scheduler = new TaskGraphScheduler();
    }

    /**
     * Initialize the kernel: register main thread, init worker context, compile graph.
     * Must be called from the main thread after pipeline initialization.
     */
    public void initialize() {
        ThreadDomainGuard.registerMainThread();

        // Initialize render worker context if GL worker is enabled
        if (GLRuntimeFlags.GL_WORKER_ENABLED) {
            long mainWindow = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
            GraphicsDriver.getCurrentAPI().initRenderWorkerContext(mainWindow);
        }

        moduleRegistry.initialize(pipeline);
        rebuildGraph();
    }

    /**
     * Rebuild the render graph from the current module set.
     */
    public void rebuildGraph() {
        RenderGraphBuilder<C> builder = new RenderGraphBuilder<>(pipeline);

        // Core pipeline passes: 3-pass cross-frame pipeline
        builder
                .addPass(new SyncCommitPass<>())
                .addPass(new SyncPreparePass<>(), SyncCommitPass.NAME)
                .addPass(new AsyncRenderPass<>(), SyncPreparePass.NAME);

        // Let each module contribute passes
        moduleRegistry.contributeToGraph(builder);

        this.compiledGraph = builder.compile();
    }

    // ==================== Tick Lifecycle ====================

    /**
     * PRE TICK: wait for async tick, swap data.
     */
    public void onPreTick() {
        if (!asyncTickTask.isDone()) {
            final String waitTimerName = "wait.async_tick_task";
            TimerUtil.COMMAND_TIMER.start(waitTimerName);
            try {
                asyncTickTask.join();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                TimerUtil.COMMAND_TIMER.end(waitTimerName);
            }
        }
        pipeline.swapGraphicsData();
    }

    /**
     * POST TICK: sync tick, then launch async tick.
     */
    public void onPostTick() {
        pipeline.tickGraphics();
        asyncTickTask = CompletableFuture.runAsync(pipeline::asyncTickGraphics, TICK_EXECUTOR);
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

        if (compiledGraph != null) {
            // Cross-frame: async pass publishes result to PendingBuildSlot;
            // waitAtFrameEnd=false so we don't block on the current frame's async build.
            scheduler.execute(compiledGraph, frameCtx);
        }
    }

    // ==================== Accessors ====================

    public GraphicsPipeline<C> pipeline() { return pipeline; }
    public ModuleRegistry moduleRegistry() { return moduleRegistry; }
    public TaskGraphScheduler scheduler() { return scheduler; }

    public boolean isGraphCompiled() {
        return compiledGraph != null;
    }

    public void publishBuildResult(BuildResult buildResult) {
        if (buildResult != null) {
            pendingBuildSlot.publish(buildResult);
        }
    }

    public BuildResult consumeBuildResult() {
        return pendingBuildSlot.consume();
    }
}
