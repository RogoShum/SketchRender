package rogo.sketch.backend.vulkan;

import org.lwjgl.vulkan.VkPhysicalDevice;

final class VulkanDeviceSelection {
    final VkPhysicalDevice physicalDevice;
    final String deviceName;
    final int graphicsQueueFamilyIndex;
    final int presentQueueFamilyIndex;
    final int computeQueueFamilyIndex;
    final int transferQueueFamilyIndex;
    final int score;

    VulkanDeviceSelection(
            VkPhysicalDevice physicalDevice,
            String deviceName,
            int graphicsQueueFamilyIndex,
            int presentQueueFamilyIndex,
            int computeQueueFamilyIndex,
            int transferQueueFamilyIndex,
            int score) {
        this.physicalDevice = physicalDevice;
        this.deviceName = deviceName;
        this.graphicsQueueFamilyIndex = graphicsQueueFamilyIndex;
        this.presentQueueFamilyIndex = presentQueueFamilyIndex;
        this.computeQueueFamilyIndex = computeQueueFamilyIndex;
        this.transferQueueFamilyIndex = transferQueueFamilyIndex;
        this.score = score;
    }
}
