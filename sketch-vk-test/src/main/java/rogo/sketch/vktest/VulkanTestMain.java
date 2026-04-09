package rogo.sketch.vktest;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.backend.opengl.OpenGLBackendBootstrap;
import rogo.sketch.backend.vulkan.VulkanBackendBootstrap;
import rogo.sketch.backend.vulkan.VulkanBackendRuntime;
import rogo.sketch.core.backend.BackendBootstrapContext;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.backend.opengl.driver.OpenGLAPI;
import rogo.sketch.core.event.bridge.EventBusBridge;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public final class VulkanTestMain {
    private VulkanTestMain() {
    }

    public static void main(String[] args) {
        BackendKind backendKind = parseBackendKind(args);
        long smokeDurationMillis = parseSmokeDurationMillis(args);
        List<ScheduledResize> scheduledResizes = parseScheduledResizes(args);
        GLFWErrorCallback errorCallback = GLFWErrorCallback.createPrint(System.err);
        errorCallback.set();

        long window = MemoryUtil.NULL;
        PipelineTestScene scene = null;
        try {
            initializeGlfw(backendKind);
            boolean visible = smokeDurationMillis <= 0L || !scheduledResizes.isEmpty();
            window = createWindow(backendKind, visible);

            if (backendKind == BackendKind.DESKTOP_GL) {
                GLFW.glfwMakeContextCurrent(window);
                GL.createCapabilities();
                GLFW.glfwSwapInterval(1);
                GraphicsDriver.registerBackendBootstrap(
                        new OpenGLBackendBootstrap(BackendKind.DESKTOP_GL, new OpenGLAPI()));
            } else {
                GraphicsDriver.registerBackendBootstrap(new VulkanBackendBootstrap());
            }

            GraphicsDriver.bootstrap(backendKind, new BackendBootstrapContext("sketch-vk-test", window));
            GraphicsDriver.runtime().registerMainThread();
            EventBusBridge.setImplementation(new VkTestEventBusImplementation());
            int[] framebufferSize = framebufferSize(window);
            scene = PipelineTestScene.create(backendKind, framebufferSize[0], framebufferSize[1]);
            installResizeCallback(window, backendKind, scene);

            printBootInfo(backendKind);

            long deadline = smokeDurationMillis > 0L ? System.nanoTime() + smokeDurationMillis * 1_000_000L : Long.MAX_VALUE;
            long startTime = System.nanoTime();
            VulkanBackendRuntime vulkanRuntime =
                    backendKind == BackendKind.VULKAN ? (VulkanBackendRuntime) GraphicsDriver.runtime() : null;

            while (!GLFW.glfwWindowShouldClose(window)) {
                GLFW.glfwPollEvents();
                if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
                    GLFW.glfwSetWindowShouldClose(window, true);
                }
                runScheduledResizes(window, scheduledResizes, startTime);
                scene.renderFrame();
                if (vulkanRuntime != null) {
                    vulkanRuntime.drawFrame();
                } else {
                    GLFW.glfwSwapBuffers(window);
                }
                if (System.nanoTime() >= deadline) {
                    GLFW.glfwSetWindowShouldClose(window, true);
                }
            }

            printShutdownStats(vulkanRuntime);
        } finally {
            if (scene != null) {
                scene.close();
            }
            GraphicsDriver.shutdown();
            if (backendKind == BackendKind.DESKTOP_GL) {
                GL.setCapabilities(null);
            }
            if (window != MemoryUtil.NULL) {
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
            errorCallback.free();
        }
    }

    private static BackendKind parseBackendKind(String[] args) {
        if (args == null) {
            return BackendKind.VULKAN;
        }
        for (String arg : args) {
            if (arg == null || !arg.startsWith("--backend=")) {
                continue;
            }
            String value = arg.substring("--backend=".length()).trim().toLowerCase();
            return switch (value) {
                case "desktop_gl", "desktop-gl", "gl" -> BackendKind.DESKTOP_GL;
                case "vulkan", "vk" -> BackendKind.VULKAN;
                default -> BackendKind.VULKAN;
            };
        }
        return BackendKind.VULKAN;
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

    private static void initializeGlfw(BackendKind backendKind) {
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Failed to initialize GLFW");
        }
        if (backendKind == BackendKind.VULKAN && !GLFWVulkan.glfwVulkanSupported()) {
            throw new IllegalStateException("GLFW Vulkan support is unavailable");
        }
    }

    private static long createWindow(BackendKind backendKind, boolean visible) {
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, visible ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        if (backendKind == BackendKind.VULKAN) {
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
        } else {
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        }

        String title = backendKind == BackendKind.VULKAN ? "Sketch Vulkan Test" : "Sketch Desktop GL Test";
        long window = GLFW.glfwCreateWindow(1280, 720, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (window == MemoryUtil.NULL) {
            throw new IllegalStateException("Failed to create GLFW window for backend " + backendKind);
        }
        return window;
    }

    private static void installResizeCallback(long window, BackendKind backendKind, PipelineTestScene scene) {
        if (scene == null) {
            return;
        }
        GLFW.glfwSetFramebufferSizeCallback(window, (handle, width, height) -> {
            scene.resize(width, height);
            if (backendKind == BackendKind.VULKAN && GraphicsDriver.runtime() instanceof VulkanBackendRuntime runtime) {
                runtime.markFramebufferResized();
            }
        });
    }

    private static int[] framebufferSize(long window) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            GLFW.glfwGetFramebufferSize(window, width, height);
            return new int[]{Math.max(1, width.get(0)), Math.max(1, height.get(0))};
        }
    }

    private static void printBootInfo(BackendKind backendKind) {
        System.out.println("Sketch test backend bootstrapped");
        System.out.println("  backend=" + GraphicsDriver.kind());
        System.out.println("  runtime=" + GraphicsDriver.runtime().backendName());
        if (backendKind == BackendKind.VULKAN && GraphicsDriver.runtime() instanceof VulkanBackendRuntime runtime) {
            System.out.println("  deviceName=" + runtime.physicalDeviceName());
            System.out.println("  swapchainFormat=" + runtime.swapchainImageFormat());
            System.out.println("  swapchainExtent=" + runtime.swapchainExtentWidth() + "x" + runtime.swapchainExtentHeight());
            System.out.println("  maxFramesInFlight=" + runtime.maxFramesInFlight());
        }
    }

    private static void printShutdownStats(VulkanBackendRuntime runtime) {
        if (runtime == null) {
            return;
        }
        System.out.println("  renderedFrames=" + runtime.renderedFrameCount());
        System.out.println("  swapchainGeneration=" + runtime.swapchainGeneration());
        System.out.println("  swapchainRecreations=" + runtime.swapchainRecreationCount());
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

