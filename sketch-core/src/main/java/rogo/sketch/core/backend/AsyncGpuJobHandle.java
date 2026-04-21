package rogo.sketch.core.backend;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.pipeline.graph.scheduler.SimpleProfiler;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class AsyncGpuJobHandle {
    private final long jobId;
    private final GpuJobType jobType;
    private final @Nullable KeyId resourceId;
    private final long epoch;
    private final CompletableFuture<AsyncGpuCompletion> completion;

    public AsyncGpuJobHandle(
            long jobId,
            GpuJobType jobType,
            @Nullable KeyId resourceId,
            long epoch,
            CompletableFuture<AsyncGpuCompletion> completion) {
        this.jobId = jobId;
        this.jobType = Objects.requireNonNull(jobType, "jobType");
        this.resourceId = resourceId;
        this.epoch = epoch;
        this.completion = Objects.requireNonNull(completion, "completion");
    }

    public long jobId() {
        return jobId;
    }

    public GpuJobType jobType() {
        return jobType;
    }

    public @Nullable KeyId resourceId() {
        return resourceId;
    }

    public long epoch() {
        return epoch;
    }

    public CompletableFuture<AsyncGpuCompletion> completion() {
        return completion;
    }

    public boolean isDone() {
        if (!completion.isDone()) {
            return false;
        }
        try {
            AsyncGpuCompletion gpuCompletion = completion.getNow(null);
            return gpuCompletion != null && gpuCompletion.isDone();
        } catch (CompletionException completionException) {
            return true;
        }
    }

    public void await() {
        String resourceLabel = resourceId != null ? resourceId.toString() : "resource:none";
        String waitLabel = "AsyncGpuWait:" + resourceLabel + ":epoch=" + epoch;
        String submitLabel = "AsyncGpuSubmitJoin:" + resourceLabel + ":epoch=" + epoch;
        String gpuLabel = "AsyncGpuFenceWait:" + resourceLabel + ":epoch=" + epoch;
        SimpleProfiler.get().begin(waitLabel, Thread.currentThread().getName());
        try {
            SimpleProfiler.get().begin(submitLabel, Thread.currentThread().getName());
            AsyncGpuCompletion gpuCompletion;
            try {
                gpuCompletion = completion.join();
            } finally {
                SimpleProfiler.get().end(submitLabel, Thread.currentThread().getName());
            }
            SimpleProfiler.get().begin(gpuLabel, Thread.currentThread().getName());
            try {
                gpuCompletion.await();
            } finally {
                SimpleProfiler.get().end(gpuLabel, Thread.currentThread().getName());
            }
        } catch (CompletionException completionException) {
            Throwable cause = completionException.getCause() != null
                    ? completionException.getCause()
                    : completionException;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Async GPU job failed", cause);
        } finally {
            SimpleProfiler.get().end(waitLabel, Thread.currentThread().getName());
        }
    }
}
