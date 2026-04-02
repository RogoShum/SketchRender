package rogo.sketch.core.pipeline.graph.scheduler;

import rogo.sketch.core.backend.BackendWorkerLane;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.CompiledRenderGraph;
import rogo.sketch.core.pipeline.graph.PassNode;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.util.TimerUtil;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes a compiled graph using a staged async-tail scheduler.
 * <p>
 * Async work is collected during graph iteration and submitted once at the tail.
 * The scheduler waits for the previous async batch at the beginning of the next execute.
 * Worker-thread GL context setup is instance-owned so different schedulers may target
 * different worker lanes such as the render worker or the dedicated tick GL worker.
 * </p>
 * <p>
 * SYNC passes run on the calling (main) thread.
 * ASYNC passes are collected during graph iteration and submitted once at the end.
 * ANY passes run on the current thread.
 * </p>
 */
public class TaskGraphScheduler {
    public enum WorkerContextMode {
        NONE,
        RENDER_ASYNC,
        TICK_ASYNC
    }

    private final ExecutorService workerPool;
    private final WorkerContextMode workerContextMode;
    private final boolean ownsWorkerPool;
    private final AtomicBoolean workerContextInitialized = new AtomicBoolean(false);
    private CompletableFuture<Void> pendingAsyncBatch = CompletableFuture.completedFuture(null);
    private volatile String pendingAsyncPassName = "none";

    public TaskGraphScheduler(ExecutorService workerPool, WorkerContextMode workerContextMode, boolean ownsWorkerPool) {
        this.workerPool = workerPool;
        this.workerContextMode = workerContextMode;
        this.ownsWorkerPool = ownsWorkerPool;
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
                        SketchDiagnostics.get().error("task-graph", "Error in SYNC pass '" + node.name() + "'", e);
                    }
                }
                case ASYNC -> {
                    asyncTaskQueue.add(() -> {
                        pendingAsyncPassName = node.name();
                        SimpleProfiler.get().begin("AsyncJob:" + node.name(), "WorkerThread");
                        try {
                            node.pass().execute(ctx);
                        } catch (Exception e) {
                            SketchDiagnostics.get().error("task-graph", "Error in ASYNC pass '" + node.name() + "'", e);
                        } finally {
                            SimpleProfiler.get().end("AsyncJob:" + node.name(), "WorkerThread");
                        }
                    });
                }
                case ANY -> {
                    try {
                        node.pass().execute(ctx);
                    } catch (Exception e) {
                        SketchDiagnostics.get().error("task-graph", "Error in ANY pass '" + node.name() + "'", e);
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
     * Wait for this scheduler's previously submitted async batch.
     */
    public void awaitPendingAsync() {
        waitForPendingAsync();
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
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                SketchDiagnostics.get().error("task-graph", "Async pass failed: " + pendingAsyncPassName, cause);
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

    private void ensureWorkerReady() {
        if (workerContextMode == WorkerContextMode.NONE) {
            return;
        }
        if (GraphicsDriver.capabilities().workerLanesSupported() && workerContextInitialized.compareAndSet(false, true)) {
            switch (workerContextMode) {
                case RENDER_ASYNC -> GraphicsDriver.runtime().onWorkerLaneStart(BackendWorkerLane.RENDER_ASYNC);
                case TICK_ASYNC -> GraphicsDriver.runtime().onWorkerLaneStart(BackendWorkerLane.TICK_ASYNC);
                case NONE -> {
                }
            }
        }
    }

    /**
     * Shut down this scheduler and release its worker context if needed.
     */
    public void shutdown() {
        waitForPendingAsync();
        if (workerContextInitialized.get()) {
            try {
                workerPool.submit(() -> {
                    switch (workerContextMode) {
                        case RENDER_ASYNC -> GraphicsDriver.runtime().onWorkerLaneEnd(BackendWorkerLane.RENDER_ASYNC);
                        case TICK_ASYNC -> GraphicsDriver.runtime().onWorkerLaneEnd(BackendWorkerLane.TICK_ASYNC);
                        case NONE -> {
                        }
                    }
                }).get(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                SketchDiagnostics.get().warn("task-graph", "Failed to cleanup worker GL context", e);
            }
        }
        if (ownsWorkerPool) {
            workerPool.shutdown();
            try {
                if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    workerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
