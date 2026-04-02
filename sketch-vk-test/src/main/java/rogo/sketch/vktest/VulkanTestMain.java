package rogo.sketch.vktest;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.backend.vulkan.VulkanBackendBootstrap;
import rogo.sketch.backend.vulkan.VulkanBackendRuntime;
import rogo.sketch.core.backend.BackendBootstrapContext;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.driver.GraphicsDriver;

import java.util.ArrayList;
import java.util.List;

public final class VulkanTestMain {
    private VulkanTestMain() {
    }

    public static void main(String[] args) {
        long smokeDurationMillis = parseSmokeDurationMillis(args);
        List<ScheduledResize> scheduledResizes = parseScheduledResizes(args);
        GLFWErrorCallback errorCallback = GLFWErrorCallback.createPrint(System.err);
        errorCallback.set();

        long window = MemoryUtil.NULL;
        try {
            initializeGlfw();
            window = createWindow(smokeDurationMillis <= 0L || !scheduledResizes.isEmpty());

            GraphicsDriver.registerBackendBootstrap(new VulkanBackendBootstrap());
            GraphicsDriver.bootstrap(BackendKind.VULKAN, new BackendBootstrapContext("sketch-vk-test", window));
            GraphicsDriver.runtime().registerMainThread();

            VulkanBackendRuntime runtime = (VulkanBackendRuntime) GraphicsDriver.runtime();
            GLFW.glfwSetFramebufferSizeCallback(window, (handle, width, height) -> runtime.markFramebufferResized());
            System.out.println("Vulkan backend bootstrapped");
            System.out.println("  backend=" + GraphicsDriver.kind());
            System.out.println("  runtime=" + runtime.backendName());
            System.out.println("  deviceName=" + runtime.physicalDeviceName());
            System.out.println("  instance=0x" + Long.toHexString(runtime.instanceHandle()));
            System.out.println("  physicalDevice=0x" + Long.toHexString(runtime.physicalDeviceHandle()));
            System.out.println("  logicalDevice=0x" + Long.toHexString(runtime.deviceHandle()));
            System.out.println("  surface=0x" + Long.toHexString(runtime.surfaceHandle()));
            System.out.println("  graphicsQueueFamily=" + runtime.graphicsQueueFamilyIndex());
            System.out.println("  presentQueueFamily=" + runtime.presentQueueFamilyIndex());
            System.out.println("  swapchain=0x" + Long.toHexString(runtime.swapchainHandle()));
            System.out.println("  swapchainImages=" + runtime.swapchainImageCount());
            System.out.println("  swapchainImageViews=" + runtime.swapchainImageViewCount());
            System.out.println("  swapchainFormat=" + runtime.swapchainImageFormat());
            System.out.println("  swapchainExtent=" + runtime.swapchainExtentWidth() + "x" + runtime.swapchainExtentHeight());
            System.out.println("  commandPool=0x" + Long.toHexString(runtime.commandPoolHandle()));
            System.out.println("  maxFramesInFlight=" + runtime.maxFramesInFlight());

            long deadline = smokeDurationMillis > 0L ? System.nanoTime() + smokeDurationMillis * 1_000_000L : Long.MAX_VALUE;
            long startTime = System.nanoTime();
            while (!GLFW.glfwWindowShouldClose(window)) {
                GLFW.glfwPollEvents();
                if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
                    GLFW.glfwSetWindowShouldClose(window, true);
                }
                runScheduledResizes(window, scheduledResizes, startTime);
                runtime.drawFrame();
                if (System.nanoTime() >= deadline) {
                    GLFW.glfwSetWindowShouldClose(window, true);
                }
            }
            System.out.println("  renderedFrames=" + runtime.renderedFrameCount());
            System.out.println("  swapchainGeneration=" + runtime.swapchainGeneration());
            System.out.println("  swapchainRecreations=" + runtime.swapchainRecreationCount());
        } finally {
            GraphicsDriver.shutdown();
            if (window != MemoryUtil.NULL) {
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
            errorCallback.free();
        }
    }

    private static long parseSmokeDurationMillis(String[] args) {
        if (args == null) {
            return 0L;
        }
        for (String arg : args) {
            if (arg == null) {
                continue;
            }
            if ("--smoke".equals(arg)) {
                return 250L;
            }
            if (arg.startsWith("--smoke=")) {
                try {
                    return Math.max(1L, Long.parseLong(arg.substring("--smoke=".length())));
                } catch (NumberFormatException ignored) {
                    return 250L;
                }
            }
        }
        return 0L;
    }

    private static List<ScheduledResize> parseScheduledResizes(String[] args) {
        List<ScheduledResize> schedules = new ArrayList<>();
        if (args == null) {
            return schedules;
        }
        for (String arg : args) {
            if (arg == null || !arg.startsWith("--resize=")) {
                continue;
            }
            String spec = arg.substring("--resize=".length()).trim();
            int atIndex = spec.indexOf('@');
            int xIndex = spec.indexOf('x');
            if (atIndex <= 0 || xIndex <= 0 || xIndex >= atIndex) {
                continue;
            }
            try {
                int width = Integer.parseInt(spec.substring(0, xIndex));
                int height = Integer.parseInt(spec.substring(xIndex + 1, atIndex));
                long delayMillis = Long.parseLong(spec.substring(atIndex + 1));
                if (width > 0 && height > 0 && delayMillis >= 0L) {
                    schedules.add(new ScheduledResize(width, height, delayMillis));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return schedules;
    }

    private static void runScheduledResizes(long window, List<ScheduledResize> schedules, long startTime) {
        if (schedules.isEmpty()) {
            return;
        }
        long elapsedMillis = (System.nanoTime() - startTime) / 1_000_000L;
        for (ScheduledResize resize : schedules) {
            if (!resize.applied && elapsedMillis >= resize.delayMillis) {
                GLFW.glfwSetWindowSize(window, resize.width, resize.height);
                resize.applied = true;
                System.out.println("  requestedResize=" + resize.width + "x" + resize.height + "@" + elapsedMillis + "ms");
            }
        }
    }

    private static void initializeGlfw() {
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Failed to initialize GLFW");
        }
        if (!GLFWVulkan.glfwVulkanSupported()) {
            throw new IllegalStateException("GLFW Vulkan support is unavailable");
        }
    }

    private static long createWindow(boolean visible) {
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, visible ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);

        long window = GLFW.glfwCreateWindow(1280, 720, "Sketch Vulkan Test", MemoryUtil.NULL, MemoryUtil.NULL);
        if (window == MemoryUtil.NULL) {
            throw new IllegalStateException("Failed to create GLFW window for Vulkan test");
        }
        return window;
    }

    private static final class ScheduledResize {
        private final int width;
        private final int height;
        private final long delayMillis;
        private boolean applied;

        private ScheduledResize(int width, int height, long delayMillis) {
            this.width = width;
            this.height = height;
            this.delayMillis = delayMillis;
        }
    }
}
