package rogo.sketch.backend.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;

import static org.lwjgl.vulkan.VK10.vkCmdEndRenderPass;

public final class VulkanPacketExecutionContext {
    private final VkCommandBuffer commandBuffer;
    private final VulkanRasterPipelineCache swapchainRasterPipelineCache;
    private final VulkanComputePipelineCache computePipelineCache;
    private final int imageIndex;
    private final MemoryStack stack;
    private final VulkanPacketExecutor executor;
    private VulkanRasterPipelineCache activeRasterPipelineCache;
    private KeyId currentRenderTargetId;
    private boolean renderPassOpen;

    VulkanPacketExecutionContext(
            VkCommandBuffer commandBuffer,
            VulkanRasterPipelineCache rasterPipelineCache,
            VulkanComputePipelineCache computePipelineCache,
            int imageIndex,
            MemoryStack stack,
            VulkanPacketExecutor executor) {
        this.commandBuffer = commandBuffer;
        this.swapchainRasterPipelineCache = rasterPipelineCache;
        this.activeRasterPipelineCache = rasterPipelineCache;
        this.computePipelineCache = computePipelineCache;
        this.imageIndex = imageIndex;
        this.stack = stack;
        this.executor = executor;
    }

    public VkCommandBuffer commandBuffer() {
        return commandBuffer;
    }

    public int imageIndex() {
        return imageIndex;
    }

    public boolean isRenderPassOpen() {
        return renderPassOpen;
    }

    public void ensureRenderPassOpen(KeyId renderTargetId) {
        KeyId normalizedTarget = executor.normalizeRenderTargetId(renderTargetId);
        if (renderPassOpen && !Objects.equals(currentRenderTargetId, normalizedTarget)) {
            ensureRenderPassClosed();
        }
        if (renderPassOpen) {
            return;
        }
        activeRasterPipelineCache = executor.resolveRasterPipelineCache(normalizedTarget, swapchainRasterPipelineCache);
        if (activeRasterPipelineCache == null) {
            return;
        }
        executor.prepareRenderTargetForRendering(commandBuffer, normalizedTarget);
        executor.beginRenderPass(
                commandBuffer,
                activeRasterPipelineCache,
                executor.framebufferIndexFor(normalizedTarget, imageIndex),
                stack);
        currentRenderTargetId = normalizedTarget;
        renderPassOpen = true;
    }

    public void ensureRenderPassClosed() {
        if (!renderPassOpen) {
            return;
        }
        vkCmdEndRenderPass(commandBuffer);
        renderPassOpen = false;
        currentRenderTargetId = null;
        activeRasterPipelineCache = swapchainRasterPipelineCache;
    }

    void closeRenderPassIfOpen() {
        ensureRenderPassClosed();
    }

    VulkanRasterPipelineCache rasterPipelineCache() {
        return activeRasterPipelineCache;
    }

    VulkanComputePipelineCache computePipelineCache() {
        return computePipelineCache;
    }

    MemoryStack stack() {
        return stack;
    }
}
