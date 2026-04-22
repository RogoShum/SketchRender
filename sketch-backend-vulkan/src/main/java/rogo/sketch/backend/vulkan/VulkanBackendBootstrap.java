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
        context.presentationController().applyWindowSettings(context.windowService());
        VulkanBootstrapArtifacts artifacts = VulkanDeviceBootstrapper.bootstrap(
                context.entryPoint(),
                context.mainWindowHandle(),
                context.windowService().vSyncEnabled());
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
                artifacts.computeQueueFamilyIndex,
                artifacts.transferQueueFamilyIndex,
                artifacts.graphicsQueue,
                artifacts.presentQueue,
                artifacts.computeQueue,
                artifacts.transferQueue,
                artifacts.swapchainHandle,
                artifacts.swapchainImageFormat,
                artifacts.swapchainExtentWidth,
                artifacts.swapchainExtentHeight,
                artifacts.swapchainImages,
                context.windowService().vSyncEnabled(),
                artifacts.debugUtilsEnabled);
    }
}

