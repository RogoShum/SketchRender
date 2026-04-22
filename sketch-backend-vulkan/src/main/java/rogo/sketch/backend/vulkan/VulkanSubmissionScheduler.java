package rogo.sketch.backend.vulkan;

import rogo.sketch.core.backend.SubmissionScheduler;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;

import java.util.ArrayList;
import java.util.List;

final class VulkanSubmissionScheduler implements SubmissionScheduler {
    private final VulkanBackendRuntime runtime;
    private final VulkanDestructionQueue destructionQueue = new VulkanDestructionQueue();
    private final List<RenderPacket> immediatePackets = new ArrayList<>();
    private volatile FrameExecutionPlan installedExecutionPlan = FrameExecutionPlan.empty();
    private volatile long[] imagesInFlight;
    private volatile boolean framebufferResized;
    private int currentFrameIndex;

    VulkanSubmissionScheduler(VulkanBackendRuntime runtime, int swapchainImageCount) {
        this.runtime = runtime;
        this.imagesInFlight = new long[Math.max(0, swapchainImageCount)];
        this.currentFrameIndex = 0;
    }

    @Override
    public void installExecutionPlan(FrameExecutionPlan plan) {
        installedExecutionPlan = plan != null ? plan : FrameExecutionPlan.empty();
    }

    @Override
    public int framesInFlight() {
        return runtime.maxFramesInFlight();
    }

    @Override
    public boolean drawFrame() {
        return runtime.drawScheduledFrame(this);
    }

    @Override
    public void markFramebufferResized() {
        framebufferResized = true;
    }

    @Override
    public void drainDeferredDestruction() {
        destructionQueue.drain();
    }

    @Override
    public void shutdown() {
        drainDeferredDestruction();
    }

    synchronized void queueImmediatePacket(RenderPacket packet) {
        if (packet != null) {
            immediatePackets.add(packet);
        }
    }

    synchronized List<RenderPacket> consumeImmediatePackets() {
        if (immediatePackets.isEmpty()) {
            return List.of();
        }
        List<RenderPacket> snapshot = List.copyOf(immediatePackets);
        immediatePackets.clear();
        return snapshot;
    }

    @Override
    public FrameExecutionPlan installedExecutionPlan() {
        return installedExecutionPlan;
    }

    boolean framebufferResized() {
        return framebufferResized;
    }

    void onSwapchainRecreated(int swapchainImageCount) {
        imagesInFlight = new long[Math.max(0, swapchainImageCount)];
        framebufferResized = false;
        currentFrameIndex = currentFrameIndex % Math.max(1, runtime.maxFramesInFlight());
    }

    VulkanFrameSlot currentFrame() {
        return runtime.frameSlot(currentFrameIndex);
    }

    int currentFrameIndex() {
        return currentFrameIndex;
    }

    long imageFence(int imageIndex) {
        return imageIndex >= 0 && imageIndex < imagesInFlight.length ? imagesInFlight[imageIndex] : 0L;
    }

    void bindImageFence(int imageIndex, long fence) {
        if (imageIndex >= 0 && imageIndex < imagesInFlight.length) {
            imagesInFlight[imageIndex] = fence;
        }
    }

    void advanceFrame() {
        currentFrameIndex = (currentFrameIndex + 1) % Math.max(1, runtime.maxFramesInFlight());
    }

    VulkanDestructionQueue destructionQueue() {
        return destructionQueue;
    }
}
