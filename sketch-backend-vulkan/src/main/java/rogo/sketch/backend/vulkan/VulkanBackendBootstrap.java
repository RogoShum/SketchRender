package rogo.sketch.backend.vulkan;

import rogo.sketch.core.backend.BackendBootstrap;
import rogo.sketch.core.backend.BackendBootstrapContext;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.backend.BackendRuntime;

public final class VulkanBackendBootstrap implements BackendBootstrap {
    @Override
    public BackendKind kind() {
        return BackendKind.VULKAN;
    }

    @Override
    public BackendRuntime bootstrap(BackendBootstrapContext context) {
        VulkanBootstrapArtifacts artifacts = VulkanDeviceBootstrapper.bootstrap(
                context.entryPoint(),
                context.mainWindowHandle());
        return new VulkanBackendRuntime(
                context.entryPoint(),
                context.mainWindowHandle(),
                artifacts.instance,
                artifacts.physicalDevice,
                artifacts.physicalDeviceName,
                artifacts.surfaceHandle,
                artifacts.device,
                artifacts.graphicsQueueFamilyIndex,
                artifacts.presentQueueFamilyIndex,
                artifacts.graphicsQueue,
                artifacts.presentQueue,
                artifacts.swapchainHandle,
                artifacts.swapchainImageFormat,
                artifacts.swapchainExtentWidth,
                artifacts.swapchainExtentHeight,
                artifacts.swapchainImages,
                artifacts.debugUtilsEnabled);
    }
}

