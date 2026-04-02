package rogo.sketch.backend.vulkan;

import org.lwjgl.vulkan.VkCommandBuffer;

final class VulkanFrameSlot {
    final VkCommandBuffer commandBuffer;
    final long imageAvailableSemaphore;
    final long renderFinishedSemaphore;
    final long inFlightFence;

    VulkanFrameSlot(
            VkCommandBuffer commandBuffer,
            long imageAvailableSemaphore,
            long renderFinishedSemaphore,
            long inFlightFence) {
        this.commandBuffer = commandBuffer;
        this.imageAvailableSemaphore = imageAvailableSemaphore;
        this.renderFinishedSemaphore = renderFinishedSemaphore;
        this.inFlightFence = inFlightFence;
    }
}
