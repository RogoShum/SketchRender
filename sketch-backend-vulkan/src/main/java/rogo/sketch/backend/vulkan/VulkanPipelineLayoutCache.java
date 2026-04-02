package rogo.sketch.backend.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import rogo.sketch.core.packet.ResourceSetKey;

import java.nio.LongBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;

final class VulkanPipelineLayoutCache {
    private final VkDevice device;
    private final Map<ResourceSetKey, Long> layouts = new ConcurrentHashMap<>();

    VulkanPipelineLayoutCache(VkDevice device) {
        this.device = device;
    }

    long layoutFor(ResourceSetKey resourceSetKey) {
        ResourceSetKey key = resourceSetKey != null ? resourceSetKey : ResourceSetKey.empty();
        return layouts.computeIfAbsent(key, ignored -> createEmptyLayout());
    }

    void destroy() {
        for (long layout : layouts.values()) {
            vkDestroyPipelineLayout(device, layout, null);
        }
        layouts.clear();
    }

    private long createEmptyLayout() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineLayoutCreateInfo createInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
            LongBuffer pipelineLayoutPointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkCreatePipelineLayout(device, createInfo, null, pipelineLayoutPointer),
                    "vkCreatePipelineLayout");
            return pipelineLayoutPointer.get(0);
        }
    }
}
