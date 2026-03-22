package rogo.sketch.core.pipeline.graph.scheduler;

import rogo.sketch.core.driver.GLRuntimeFlags;
import rogo.sketch.core.driver.GraphicsAPI;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.CompiledRenderGraph;
import rogo.sketch.core.pipeline.graph.PassNode;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;
import rogo.sketch.core.util.TimerUtil;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes a compiled graph using a staged async-tail scheduler.
 * <p>
 * The render worker thread (Sketch-RenderTask-Worker) acquires the shared GL context
 * on first task execution and releases it on shutdown, managed via
 * {@link GraphicsAPI#onWorkerThreadStart()} / {@link GraphicsAPI#onWorkerThreadEnd()}.
 * </p>
 * <p>
 * SYNC passes run on the calling (main) thread.
 * ASYNC passes are collected during graph iteration and submitted once at the end.
 * The scheduler waits for the previous async batch at the beginning of the next execute.
 * ANY passes run on the current thread.
 * </p>
 */
public class TaskGraphScheduler {

    private static final AtomicBoolean workerContextInitialized = new AtomicBoolean(false);

    private static final ExecutorService WORKER_POOL =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Sketch-RenderTask-Worker");
                t.setDaemon(true);
                return t;
            });

    private final ExecutorService workerPool;
    private final boolean manageGlContext;
    private CompletableFuture<Void> pendingAsyncBatch = CompletableFuture.completedFuture(null);
    private volatile String pendingAsyncPassName = "none";

    public TaskGraphScheduler() {
        this(WORKER_POOL, true);
    }

    public TaskGraphScheduler(ExecutorService workerPool, boolean manageGlContext) {
        this.workerPool = workerPool;
        this.manageGlContext = manageGlContext;
    }

    /**
     * Execute all passes in the compiled graph.
     *
     */
    public <C extends RenderContext> void execute(CompiledRenderGraph<C> graph, FrameContext<C> ctx) {
        execute(graph, ctx, false);
    }

    /**
     * Execute all passes in the compiled graph and optionally wait for the async
     * tail batch before returning.
     */
    public <C extends RenderContext> void execute(CompiledRenderGraph<C> graph, FrameContext<C> ctx,
                                                  boolean waitForAsyncAtEnd) {
        SimpleProfiler.get().begin("execute", "MainThread");

        waitForPendingAsync();
        Queue<Runnable> asyncTaskQueue = new ArrayDeque<>();

        for (PassNode<C> node : graph.sortedPasses()) {
            ThreadDomain domain = node.threadDomain();

            switch (domain) {
                case SYNC -> {
                    try {
                        node.pass().execute(ctx);
                    } catch (Exception e) {
                        System.err.println("[TaskGraphScheduler] Error in SYNC pass '" + node.name() + "': " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                case ASYNC -> {
                    asyncTaskQueue.add(() -> {
                        pendingAsyncPassName = node.name();
                        SimpleProfiler.get().begin("AsyncJob:" + node.name(), "WorkerThread");
                        try {
                            node.pass().execute(ctx);
                        } catch (Exception e) {
                            System.err.println("[TaskGraphScheduler] Error in ASYNC pass '" + node.name() + "': " + e.getMessage());
                            e.printStackTrace();
                        } finally {
                            SimpleProfiler.get().end("AsyncJob:" + node.name(), "WorkerThread");
                        }
                    });
                }
                case ANY -> {
                    try {
                        node.pass().execute(ctx);
                    } catch (Exception e) {
                        System.err.println("[TaskGraphScheduler] Error in ANY pass '" + node.name() + "': " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }

        if (!asyncTaskQueue.isEmpty()) {
            submitAsyncQueue(asyncTaskQueue);
            if (waitForAsyncAtEnd) {
                waitForPendingAsync();
            }
        }

        SimpleProfiler.get().end("execute", "MainThread");
    }

    /**
     * Wait for any pending async work to complete.
     */
    private void waitForPendingAsync() {
        if (!pendingAsyncBatch.isDone()) {
            String waitTimerName = "wait.async_render_pass." + pendingAsyncPassName;
            TimerUtil.COMMAND_TIMER.start(waitTimerName);
            SimpleProfiler.get().begin("MainWait:" + pendingAsyncPassName, "MainThread");
            try {
                pendingAsyncBatch.join();
            } catch (CompletionException e) {
                System.err.println("[TaskGraphScheduler] Async pass failed: " + e.getCause().getMessage());
                e.getCause().printStackTrace();
            } finally {
                SimpleProfiler.get().end("MainWait:" + pendingAsyncPassName, "MainThread");
                TimerUtil.COMMAND_TIMER.end(waitTimerName);
                pendingAsyncPassName = "none";
            }
        }
    }

    private void submitAsyncQueue(Queue<Runnable> asyncTaskQueue) {
        SimpleProfiler.get().begin("Submit AsyncBatch", "MainThread");

        pendingAsyncBatch = CompletableFuture.runAsync(() -> {
            ensureWorkerReady();

            Runnable task;
            while ((task = asyncTaskQueue.poll()) != null) {
                task.run();
            }
        }, workerPool);

        SimpleProfiler.get().end("Submit AsyncBatch", "MainThread");
    }

    /**
     * Initialize the GL context on the worker thread if not already done.
     * Called once on the worker thread.
     */
    private static void ensureWorkerContextInitialized() {
        if (GLRuntimeFlags.GL_WORKER_ENABLED && workerContextInitialized.compareAndSet(false, true)) {
            GraphicsAPI api = GraphicsDriver.getCurrentAPI();
            api.onWorkerThreadStart();
        }
    }

    private void ensureWorkerReady() {
        if (manageGlContext) {
            TaskGraphScheduler.ensureWorkerContextInitialized();
        }
    }

    /**
     * Shut down the worker pool and release GL context.
     */
    public static void shutdown() {
        // Submit context cleanup task
        if (workerContextInitialized.get()) {
            try {
                WORKER_POOL.submit(() -> {
                    GraphicsDriver.getCurrentAPI().onWorkerThreadEnd();
                }).get(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.err.println("[TaskGraphScheduler] Failed to cleanup worker GL context: " + e.getMessage());
            }
        }
        WORKER_POOL.shutdown();
        try {
            if (!WORKER_POOL.awaitTermination(5, TimeUnit.SECONDS)) {
                WORKER_POOL.shutdownNow();
            }
        } catch (InterruptedException e) {
            WORKER_POOL.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

