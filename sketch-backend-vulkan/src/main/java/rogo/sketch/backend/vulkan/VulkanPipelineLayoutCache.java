package rogo.sketch.backend.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import rogo.sketch.core.util.KeyId;

import java.nio.LongBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;

final class VulkanPipelineLayoutCache {
    private final VkDevice device;
    private final Map<LayoutKey, Long> layouts = new ConcurrentHashMap<>();

    VulkanPipelineLayoutCache(VkDevice device) {
        this.device = device;
    }

    long layoutFor(KeyId resourceLayoutKey, long descriptorSetLayout) {
        LayoutKey key = new LayoutKey(
                resourceLayoutKey != null ? resourceLayoutKey : KeyId.of("sketch:empty_resource_layout"),
                descriptorSetLayout);
        return layouts.computeIfAbsent(key, this::createLayout);
    }

    void destroy() {
        for (long layout : layouts.values()) {
            vkDestroyPipelineLayout(device, layout, null);
        }
        layouts.clear();
    }

    private long createLayout(LayoutKey key) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineLayoutCreateInfo createInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
            if (key.descriptorSetLayout() != VK_NULL_HANDLE) {
                createInfo.pSetLayouts(stack.longs(key.descriptorSetLayout()));
            }
            LongBuffer pipelineLayoutPointer = stack.mallocLong(1);
            VulkanDeviceBootstrapper.checkVkResult(
                    vkCreatePipelineLayout(device, createInfo, null, pipelineLayoutPointer),
                    "vkCreatePipelineLayout");
            return pipelineLayoutPointer.get(0);
        }
    }

    private record LayoutKey(KeyId resourceLayoutKey, long descriptorSetLayout) {
    }
}

