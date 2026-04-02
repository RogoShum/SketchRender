package rogo.sketch.backend.vulkan;

import org.lwjgl.vulkan.VkPhysicalDevice;

final class VulkanDeviceSelection {
    final VkPhysicalDevice physicalDevice;
    final String deviceName;
    final int graphicsQueueFamilyIndex;
    final int presentQueueFamilyIndex;
    final int score;

    VulkanDeviceSelection(
            VkPhysicalDevice physicalDevice,
            String deviceName,
            int graphicsQueueFamilyIndex,
            int presentQueueFamilyIndex,
            int score) {
        this.physicalDevice = physicalDevice;
        this.deviceName = deviceName;
        this.graphicsQueueFamilyIndex = graphicsQueueFamilyIndex;
        this.presentQueueFamilyIndex = presentQueueFamilyIndex;
        this.score = score;
    }
}
