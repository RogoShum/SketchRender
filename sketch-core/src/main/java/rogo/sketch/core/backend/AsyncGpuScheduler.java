package rogo.sketch.core.backend;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.debug.RenderDocRuntime;
import rogo.sketch.core.packet.ClearPacket;
import rogo.sketch.core.packet.DrawPacket;
import rogo.sketch.core.packet.ExecutionDomain;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.scheduler.SimpleProfiler;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.pipeline.PipelineConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Backend-neutral async GPU task scheduler used by both runClient and vk-test.
 * It deliberately operates on immutable packet batches and waits only on the
 * returned job handles instead of forcing frame-global synchronization.
 */
public final class AsyncGpuScheduler {
    private final LaneScheduler computeLane;
    private final LaneScheduler uploadLane;
    private final LaneScheduler graphicsLane;
    private final AtomicLong jobIds = new AtomicLong();
    private final ConcurrentHashMap<Long, AsyncGpuJobHandle> handles = new ConcurrentHashMap<>();

    public AsyncGpuScheduler(
            ExecutorService computeExecutor,
            ExecutorService uploadExecutor,
            ExecutorService graphicsExecutor,
            boolean ownsExecutors) {
        this.computeLane = new LaneScheduler(computeExecutor, BackendWorkerLane.COMPUTE_ASYNC, ownsExecutors);
        this.uploadLane = new LaneScheduler(uploadExecutor, BackendWorkerLane.UPLOAD_ASYNC, ownsExecutors);
        this.graphicsLane = new LaneScheduler(graphicsExecutor, BackendWorkerLane.OFFSCREEN_GRAPHICS_ASYNC, ownsExecutors);
    }

    public <C extends RenderContext> AsyncGpuJobHandle submitCompute(AsyncComputeRequest<C> request) {
        Objects.requireNonNull(request, "request");
        LaneScheduler lane = selectLaneForAffinity(
                new QueueAffinity(ExecutionDomain.COMPUTE, true),
                computeLane,
                graphicsLane,
                uploadLane);
        return submit(
                GpuJobType.COMPUTE,
                request.pipeline(),
                request.renderContext(),
                request.packets(),
                request.resourceId(),
                request.epoch(),
                request.completionCallback(),
                lane);
    }

    public <C extends RenderContext> AsyncGpuJobHandle submitUpload(AsyncUploadRequest<C> request) {
        Objects.requireNonNull(request, "request");
        LaneScheduler lane = selectLaneForAffinity(
                new QueueAffinity(ExecutionDomain.TRANSFER, true),
                uploadLane,
                graphicsLane,
                computeLane);
        return submit(
                GpuJobType.UPLOAD,
                request.pipeline(),
                request.renderContext(),
                request.packets(),
                request.resourceId(),
                request.epoch(),
                request.completionCallback(),
                lane);
    }

    public <C extends RenderContext> AsyncGpuJobHandle submitGraphics(AsyncGraphicsRequest<C> request) {
        Objects.requireNonNull(request, "request");
        if (!graphicsLane.supported()) {
            throw new IllegalStateException("Backend does not support async offscreen graphics");
        }
        validateOffscreenGraphicsPackets(request.packets());
        return submit(
                GpuJobType.OFFSCREEN_GRAPHICS,
                request.pipeline(),
                request.renderContext(),
                request.packets(),
                request.resourceId(),
                request.epoch(),
                request.completionCallback(),
                graphicsLane);
    }

    public void await(@Nullable AsyncGpuJobHandle handle) {
        if (handle != null) {
            handle.await();
        }
    }

    public boolean isDone(@Nullable AsyncGpuJobHandle handle) {
        return handle == null || handle.isDone();
    }

    public void drain() {
        List<AsyncGpuJobHandle> snapshot = new ArrayList<>(handles.values());
        for (AsyncGpuJobHandle handle : snapshot) {
            await(handle);
        }
    }

    public void shutdown() {
        drain();
        computeLane.shutdown();
        uploadLane.shutdown();
        graphicsLane.shutdown();
        handles.clear();
    }

    private <C extends RenderContext> AsyncGpuJobHandle submit(
            GpuJobType jobType,
            rogo.sketch.core.pipeline.GraphicsPipeline<C> pipeline,
            C renderContext,
            List<RenderPacket> packets,
            @Nullable rogo.sketch.core.util.KeyId resourceId,
            long epoch,
            @Nullable Runnable completionCallback,
            LaneScheduler laneScheduler) {
        if (pipeline == null || renderContext == null || packets == null || packets.isEmpty()) {
            return new AsyncGpuJobHandle(
                    0L,
                    jobType,
                    resourceId,
                    epoch,
                    CompletableFuture.completedFuture(AsyncGpuCompletion.completed()));
        }
        long jobId = jobIds.incrementAndGet();
        C snapshot = cloneContext(renderContext);
        List<RenderPacket> packetSnapshot = List.copyOf(packets);
        String resourceLabel = resourceId != null ? resourceId.toString() : "resource:none";
        String submitLabel = "AsyncGpuSubmit:" + jobType + ":" + resourceLabel;
        String jobLabel = "AsyncGpuJob:" + jobType + ":" + resourceLabel + ":epoch=" + epoch;
        SimpleProfiler.get().begin(submitLabel, "MainThread");
        CompletableFuture<AsyncGpuCompletion> completion;
        if (shouldExecuteInlineOnCallerThread(jobType)) {
            completion = submitInline(jobType, pipeline, snapshot, packetSnapshot, completionCallback, resourceLabel, jobLabel);
        } else {
            completion = laneScheduler.submit(() -> {
                SimpleProfiler.get().begin(jobLabel, Thread.currentThread().getName());
                try {
                    AsyncGpuCompletion gpuCompletion = GraphicsDriver.renderDevice().submitAsyncPackets(pipeline, packetSnapshot, snapshot);
                    if (completionCallback != null) {
                        completionCallback.run();
                    }
                    return gpuCompletion != null ? gpuCompletion : AsyncGpuCompletion.completed();
                } finally {
                    SimpleProfiler.get().end(jobLabel, Thread.currentThread().getName());
                }
            });
        }
        SimpleProfiler.get().end(submitLabel, "MainThread");
        AsyncGpuJobHandle handle = new AsyncGpuJobHandle(jobId, jobType, resourceId, epoch, completion);
        handles.put(jobId, handle);
        completion.whenComplete((ignored, throwable) -> handles.remove(jobId));
        return handle;
    }

    private <C extends RenderContext> CompletableFuture<AsyncGpuCompletion> submitInline(
            GpuJobType jobType,
            rogo.sketch.core.pipeline.GraphicsPipeline<C> pipeline,
            C renderContext,
            List<RenderPacket> packets,
            @Nullable Runnable completionCallback,
            String resourceLabel,
            String jobLabel) {
        CompletableFuture<AsyncGpuCompletion> completion = new CompletableFuture<>();
        String inlineLabel = "AsyncGpuInline:" + jobType + ":" + resourceLabel;
        SimpleProfiler.get().begin(inlineLabel, Thread.currentThread().getName());
        try {
            SimpleProfiler.get().begin(jobLabel, Thread.currentThread().getName());
            try {
                AsyncGpuCompletion gpuCompletion = GraphicsDriver.renderDevice().submitAsyncPackets(pipeline, packets, renderContext);
                if (completionCallback != null) {
                    completionCallback.run();
                }
                completion.complete(gpuCompletion != null ? gpuCompletion : AsyncGpuCompletion.completed());
            } finally {
                SimpleProfiler.get().end(jobLabel, Thread.currentThread().getName());
            }
        } catch (Throwable throwable) {
            SketchDiagnostics.get().error("async-gpu",
                    "Inline async GPU job failed while GL async workers were disabled", throwable);
            completion.completeExceptionally(throwable);
        } finally {
            SimpleProfiler.get().end(inlineLabel, Thread.currentThread().getName());
        }
        return completion;
    }

    private boolean shouldExecuteInlineOnCallerThread(GpuJobType jobType) {
        BackendKind kind = GraphicsDriver.kind();
        boolean glBackend = kind == BackendKind.OPENGL;
        if (!glBackend) {
            return false;
        }
        if (RuntimeDebugToggles.glAsyncGpuWorkersDisabled()) {
            return true;
        }
        return jobType == GpuJobType.OFFSCREEN_GRAPHICS && RenderDocRuntime.enabled();
    }

    @SuppressWarnings("unchecked")
    private static <C extends RenderContext> C cloneContext(C renderContext) {
        return (C) renderContext.snapshot();
    }

    private LaneScheduler selectLane(LaneScheduler preferred, LaneScheduler fallback) {
        if (preferred.supported()) {
            return preferred;
        }
        if (fallback != null && fallback.supported()) {
            return fallback;
        }
        return preferred;
    }

    private LaneScheduler selectLaneForAffinity(
            QueueAffinity affinity,
            LaneScheduler preferred,
            LaneScheduler sharedFallback,
            LaneScheduler compatibilityFallback) {
        QueueRouter queueRouter = GraphicsDriver.queueRouter();
        if (queueRouter != null && affinity != null && queueRouter.resolveQueue(affinity).isValid()) {
            if (queueRouter.isDedicatedQueue(affinity.domain()) && preferred.supported()) {
                return preferred;
            }
            if (sharedFallback != null && sharedFallback.supported()) {
                return sharedFallback;
            }
        }
        return selectLane(preferred, compatibilityFallback);
    }

    private static void validateOffscreenGraphicsPackets(List<RenderPacket> packets) {
        if (packets == null || packets.isEmpty()) {
            throw new IllegalArgumentException("Async graphics packets must not be empty");
        }
        for (RenderPacket packet : packets) {
            if (packet == null) {
                continue;
            }
            if (packet instanceof DrawPacket drawPacket) {
                if (drawPacket.stateKey().domain() != ExecutionDomain.OFFSCREEN_GRAPHICS) {
                    throw new IllegalArgumentException(
                            "Async graphics draw packets must use OFFSCREEN_GRAPHICS domain, got "
                                    + drawPacket.stateKey().domain());
                }
                if (PipelineConfig.DEFAULT_RENDER_TARGET_ID.equals(drawPacket.stateKey().renderTargetKey())) {
                    throw new IllegalArgumentException("Async graphics draw packets must not target the default framebuffer");
                }
                continue;
            }
            if (packet instanceof ClearPacket clearPacket) {
                if (clearPacket.renderTargetId() == null
                        || PipelineConfig.DEFAULT_RENDER_TARGET_ID.equals(clearPacket.renderTargetId())) {
                    throw new IllegalArgumentException("Async graphics clear packets must target backend-owned offscreen render targets");
                }
                continue;
            }
            throw new IllegalArgumentException(
                    "Async graphics only supports offscreen draw/clear packets, got " + packet.packetType());
        }
    }

    private static final class LaneScheduler {
        private final ExecutorService executor;
        private final BackendWorkerLane lane;
        private final boolean ownsExecutor;
        private final AtomicBoolean workerStarted = new AtomicBoolean(false);

        private LaneScheduler(ExecutorService executor, BackendWorkerLane lane, boolean ownsExecutor) {
            this.executor = Objects.requireNonNull(executor, "executor");
            this.lane = Objects.requireNonNull(lane, "lane");
            this.ownsExecutor = ownsExecutor;
        }

        boolean supported() {
            return GraphicsDriver.capabilities().supportsLane(lane);
        }

        <T> CompletableFuture<T> submit(Supplier<T> task) {
            return CompletableFuture.supplyAsync(() -> {
                ensureWorkerStarted();
                try {
                    return task.get();
                } catch (Throwable throwable) {
                    SketchDiagnostics.get().error("async-gpu", "Async GPU job failed on lane '" + lane + "'", throwable);
                    throw throwable;
                }
            }, executor);
        }

        void shutdown() {
            if (workerStarted.get()) {
                try {
                    executor.submit(() -> GraphicsDriver.threadContext().onWorkerLaneEnd(lane)).get(2, TimeUnit.SECONDS);
                } catch (Exception e) {
                    SketchDiagnostics.get().warn("async-gpu", "Failed to cleanup async GPU worker lane '" + lane + "'", e);
                }
            }
            if (ownsExecutor) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException interruptedException) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void ensureWorkerStarted() {
            if (!GraphicsDriver.capabilities().supportsLane(lane)) {
                return;
            }
            if (workerStarted.compareAndSet(false, true)) {
                GraphicsDriver.threadContext().onWorkerLaneStart(lane);
            }
        }
    }
}
