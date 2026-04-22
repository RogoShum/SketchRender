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
    final int computeQueueFamilyIndex;
    final int transferQueueFamilyIndex;
    final VkQueue graphicsQueue;
    final VkQueue presentQueue;
    final VkQueue computeQueue;
    final VkQueue transferQueue;
    final long swapchainHandle;
    final int swapchainImageFormat;
    final int swapchainExtentWidth;
    final int swapchainExtentHeight;
    final long[] swapchainImages;
    final boolean debugUtilsEnabled;

    VulkanBootstrapArtifacts(
            VkInstance instance,
            VkPhysicalDevice physicalDevice,
            String physicalDeviceName,
            long surfaceHandle,
            VkDevice device,
            int graphicsQueueFamilyIndex,
            int presentQueueFamilyIndex,
            int computeQueueFamilyIndex,
            int transferQueueFamilyIndex,
            VkQueue graphicsQueue,
            VkQueue presentQueue,
            VkQueue computeQueue,
            VkQueue transferQueue,
            long swapchainHandle,
            int swapchainImageFormat,
            int swapchainExtentWidth,
            int swapchainExtentHeight,
            long[] swapchainImages,
            boolean debugUtilsEnabled) {
        this.instance = instance;
        this.physicalDevice = physicalDevice;
        this.physicalDeviceName = physicalDeviceName;
        this.surfaceHandle = surfaceHandle;
        this.device = device;
        this.graphicsQueueFamilyIndex = graphicsQueueFamilyIndex;
        this.presentQueueFamilyIndex = presentQueueFamilyIndex;
        this.computeQueueFamilyIndex = computeQueueFamilyIndex;
        this.transferQueueFamilyIndex = transferQueueFamilyIndex;
        this.graphicsQueue = graphicsQueue;
        this.presentQueue = presentQueue;
        this.computeQueue = computeQueue;
        this.transferQueue = transferQueue;
        this.swapchainHandle = swapchainHandle;
        this.swapchainImageFormat = swapchainImageFormat;
        this.swapchainExtentWidth = swapchainExtentWidth;
        this.swapchainExtentHeight = swapchainExtentHeight;
        this.swapchainImages = swapchainImages != null ? swapchainImages.clone() : new long[0];
        this.debugUtilsEnabled = debugUtilsEnabled;
    }
}

