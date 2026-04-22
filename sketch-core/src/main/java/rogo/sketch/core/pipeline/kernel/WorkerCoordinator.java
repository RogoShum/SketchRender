package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.backend.AsyncGpuScheduler;
import rogo.sketch.core.backend.BackendWorkerLane;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.pipeline.graph.scheduler.TaskGraphScheduler;
import rogo.sketch.core.pipeline.graph.scheduler.TaskGraphScheduler.WorkerContextMode;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class WorkerCoordinator {
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

    WorkerCoordinator() {
        this.tickExecutor = newSingleThreadExecutor("Sketch-TickTask-Worker");
        this.tickGlExecutor = newSingleThreadExecutor("Sketch-GpuTick-Worker");
        this.frameExecutor = newSingleThreadExecutor("Sketch-FrameTask-Worker");
        this.gpuComputeExecutor = newSingleThreadExecutor("Sketch-GpuCompute-Worker");
        this.gpuUploadExecutor = newSingleThreadExecutor("Sketch-GpuUpload-Worker");
        this.gpuGraphicsExecutor = newSingleThreadExecutor("Sketch-GpuGraphics-Worker");
        this.tickScheduler = new TaskGraphScheduler(tickExecutor, WorkerContextMode.NONE, true);
        this.tickGlScheduler = new TaskGraphScheduler(tickGlExecutor, WorkerContextMode.TICK_ASYNC, true);
        this.frameScheduler = new TaskGraphScheduler(frameExecutor, WorkerContextMode.RENDER_ASYNC, true);
        this.asyncGpuScheduler = new AsyncGpuScheduler(gpuComputeExecutor, gpuUploadExecutor, gpuGraphicsExecutor, true);
    }

    void initializeBackendWorkerLanes() {
        if (!GraphicsDriver.capabilities().workerLanesSupported()) {
            return;
        }
        GraphicsDriver.threadContext().initializeWorkerLane(BackendWorkerLane.RENDER_ASYNC);
        GraphicsDriver.threadContext().initializeWorkerLane(BackendWorkerLane.TICK_ASYNC);
        if (GraphicsDriver.capabilities().supportsLane(BackendWorkerLane.COMPUTE_ASYNC)) {
            GraphicsDriver.threadContext().initializeWorkerLane(BackendWorkerLane.COMPUTE_ASYNC);
        }
        if (GraphicsDriver.capabilities().supportsLane(BackendWorkerLane.UPLOAD_ASYNC)) {
            GraphicsDriver.threadContext().initializeWorkerLane(BackendWorkerLane.UPLOAD_ASYNC);
        }
        if (GraphicsDriver.capabilities().supportsLane(BackendWorkerLane.OFFSCREEN_GRAPHICS_ASYNC)) {
            GraphicsDriver.threadContext().initializeWorkerLane(BackendWorkerLane.OFFSCREEN_GRAPHICS_ASYNC);
        }
    }

    void shutdown() {
        tickScheduler.shutdown();
        tickGlScheduler.shutdown();
        frameScheduler.shutdown();
        asyncGpuScheduler.shutdown();

        if (!GraphicsDriver.capabilities().workerLanesSupported()) {
            return;
        }
        if (GraphicsDriver.capabilities().supportsLane(BackendWorkerLane.OFFSCREEN_GRAPHICS_ASYNC)) {
            GraphicsDriver.threadContext().destroyWorkerLane(BackendWorkerLane.OFFSCREEN_GRAPHICS_ASYNC);
        }
        if (GraphicsDriver.capabilities().supportsLane(BackendWorkerLane.UPLOAD_ASYNC)) {
            GraphicsDriver.threadContext().destroyWorkerLane(BackendWorkerLane.UPLOAD_ASYNC);
        }
        if (GraphicsDriver.capabilities().supportsLane(BackendWorkerLane.COMPUTE_ASYNC)) {
            GraphicsDriver.threadContext().destroyWorkerLane(BackendWorkerLane.COMPUTE_ASYNC);
        }
        GraphicsDriver.threadContext().destroyWorkerLane(BackendWorkerLane.TICK_ASYNC);
        GraphicsDriver.threadContext().destroyWorkerLane(BackendWorkerLane.RENDER_ASYNC);
    }

    TaskGraphScheduler tickScheduler() {
        return tickScheduler;
    }

    TaskGraphScheduler tickGlScheduler() {
        return tickGlScheduler;
    }

    TaskGraphScheduler frameScheduler() {
        return frameScheduler;
    }

    AsyncGpuScheduler asyncGpuScheduler() {
        return asyncGpuScheduler;
    }

    private static ExecutorService newSingleThreadExecutor(String threadName) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }
}
