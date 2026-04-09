package rogo.sketch.backend.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

import static org.lwjgl.vulkan.VK10.vkCmdEndRenderPass;

public final class VulkanPacketExecutionContext {
    private final VkCommandBuffer commandBuffer;
    private final VulkanRasterPipelineCache rasterPipelineCache;
    private final VulkanComputePipelineCache computePipelineCache;
    private final int imageIndex;
    private final MemoryStack stack;
    private final VulkanPacketExecutor executor;
    private boolean renderPassOpen;

    VulkanPacketExecutionContext(
            VkCommandBuffer commandBuffer,
            VulkanRasterPipelineCache rasterPipelineCache,
            VulkanComputePipelineCache computePipelineCache,
            int imageIndex,
            MemoryStack stack,
            VulkanPacketExecutor executor) {
        this.commandBuffer = commandBuffer;
        this.rasterPipelineCache = rasterPipelineCache;
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

    public void ensureRenderPassOpen() {
        if (renderPassOpen) {
            return;
        }
        executor.beginRenderPass(commandBuffer, rasterPipelineCache, imageIndex, stack);
        renderPassOpen = true;
    }

    public void ensureRenderPassClosed() {
        if (!renderPassOpen) {
            return;
        }
        vkCmdEndRenderPass(commandBuffer);
        renderPassOpen = false;
    }

    void closeRenderPassIfOpen() {
        ensureRenderPassClosed();
    }

    VulkanRasterPipelineCache rasterPipelineCache() {
        return rasterPipelineCache;
    }

    VulkanComputePipelineCache computePipelineCache() {
        return computePipelineCache;
    }

    MemoryStack stack() {
        return stack;
    }
}
