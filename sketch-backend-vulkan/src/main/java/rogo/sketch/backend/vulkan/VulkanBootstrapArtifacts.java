package rogo.sketch.backend.vulkan;

import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;

final class VulkanBootstrapArtifacts {
    final VkInstance instance;
    final VkPhysicalDevice physicalDevice;
    final String physicalDeviceName;
    final long surfaceHandle;
    final VkDevice device;
    final int graphicsQueueFamilyIndex;
    final int presentQueueFamilyIndex;
    final VkQueue graphicsQueue;
    final VkQueue presentQueue;
    final long swapchainHandle;
    final int swapchainImageFormat;
    final int swapchainExtentWidth;
    final int swapchainExtentHeight;
    final long[] swapchainImages;

    VulkanBootstrapArtifacts(
            VkInstance instance,
            VkPhysicalDevice physicalDevice,
            String physicalDeviceName,
            long surfaceHandle,
            VkDevice device,
            int graphicsQueueFamilyIndex,
            int presentQueueFamilyIndex,
            VkQueue graphicsQueue,
            VkQueue presentQueue,
            long swapchainHandle,
            int swapchainImageFormat,
            int swapchainExtentWidth,
            int swapchainExtentHeight,
            long[] swapchainImages) {
        this.instance = instance;
        this.physicalDevice = physicalDevice;
        this.physicalDeviceName = physicalDeviceName;
        this.surfaceHandle = surfaceHandle;
        this.device = device;
        this.graphicsQueueFamilyIndex = graphicsQueueFamilyIndex;
        this.presentQueueFamilyIndex = presentQueueFamilyIndex;
        this.graphicsQueue = graphicsQueue;
        this.presentQueue = presentQueue;
        this.swapchainHandle = swapchainHandle;
        this.swapchainImageFormat = swapchainImageFormat;
        this.swapchainExtentWidth = swapchainExtentWidth;
        this.swapchainExtentHeight = swapchainExtentHeight;
        this.swapchainImages = swapchainImages != null ? swapchainImages.clone() : new long[0];
    }
}
