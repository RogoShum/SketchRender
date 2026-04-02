package rogo.sketch.backend.vulkan;

import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import rogo.sketch.core.packet.ResourceSetKey;

final class VulkanDescriptorArena {
    @SuppressWarnings("unused")
    private final VkDevice device;

    VulkanDescriptorArena(VkDevice device) {
        this.device = device;
    }

    void bindResources(VkCommandBuffer commandBuffer, long pipelineLayout, ResourceSetKey resourceSetKey) {
        // Stage-1 Vulkan executor keeps descriptors empty. The layout cache still
        // exists so packet/resource keys can stabilize ahead of richer bindings.
    }

    void destroy() {
        // No descriptor pools are created in the stage-1 path yet.
    }
}
