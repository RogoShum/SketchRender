package rogo.sketch.backend.vulkan;

import rogo.sketch.core.backend.AsyncGpuCompletion;

final class VulkanAsyncFenceCompletion implements AsyncGpuCompletion {
    private final VulkanBackendRuntime runtime;
    private final long commandPool;
    private long commandBufferHandle;
    private long fence;
    private boolean completed;

    VulkanAsyncFenceCompletion(
            VulkanBackendRuntime runtime,
            long commandPool,
            long commandBufferHandle,
            long fence) {
        this.runtime = runtime;
        this.commandPool = commandPool;
        this.commandBufferHandle = commandBufferHandle;
        this.fence = fence;
        this.completed = fence == 0L || commandBufferHandle == 0L;
    }

    @Override
    public synchronized boolean isDone() {
        if (completed) {
            return true;
        }
        if (runtime.pollSubmittedFence(fence)) {
            runtime.releaseSubmittedCommand(commandPool, commandBufferHandle, fence);
            completed = true;
            commandBufferHandle = 0L;
            fence = 0L;
            return true;
        }
        return false;
    }

    @Override
    public synchronized void await() {
        if (completed) {
            return;
        }
        runtime.awaitSubmittedFence(fence);
        runtime.releaseSubmittedCommand(commandPool, commandBufferHandle, fence);
        completed = true;
        commandBufferHandle = 0L;
        fence = 0L;
    }
}
