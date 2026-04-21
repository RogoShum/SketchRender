package rogo.sketch.vktest;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.backend.opengl.OpenGLBackendBootstrap;
import rogo.sketch.backend.vulkan.VulkanBackendBootstrap;
import rogo.sketch.backend.vulkan.VulkanBackendRuntime;
import rogo.sketch.core.backend.BackendInstalledBindableResource;
import rogo.sketch.core.backend.BackendIndirectBuffer;
import rogo.sketch.core.backend.BackendStorageBuffer;
import rogo.sketch.core.backend.BackendBootstrapContext;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.backend.PresentationController;
import rogo.sketch.core.backend.WindowDisplayMode;
import rogo.sketch.core.backend.WindowService;
import rogo.sketch.core.backend.BackendCapabilities;
import rogo.sketch.core.dashboard.DashboardMemorySectionBuilder;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.memory.MemoryDebugSnapshot;
import rogo.sketch.core.memory.UnifiedMemoryFabric;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.pipeline.graph.scheduler.SimpleProfiler;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceBinding;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.uniform.ResourceUniformSet;
import rogo.sketch.backend.opengl.driver.OpenGLAPI;
import rogo.sketch.core.event.bridge.EventBusBridge;
import rogo.sketch.core.shader.uniform.UniformGroupSet;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.module.culling.TerrainMeshCounterBuffer;
import rogo.sketch.module.culling.TerrainMeshIndirectBuffer;
import rogo.sketch.module.culling.TerrainMeshReadbackBuffer;
import rogo.sketch.module.culling.TerrainMeshResourceSet;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class VulkanTestMain {
    private static final KeyId TERRAIN_SMOKE_STAGE_ID = KeyId.of("sketch_vktest:terrain_mesh_smoke_stage");
    private static final KeyId TERRAIN_SMOKE_SHADER_ID = KeyId.of("sketch_vktest:terrain_mesh_smoke_shader");
    private static final KeyId TERRAIN_SMOKE_MESH_BUFFER_ID = KeyId.of("sketch_vktest:terrain_mesh_smoke_mesh_buffer");
    private static final KeyId TERRAIN_SMOKE_REGION_INDEX_ID = KeyId.of("sketch_vktest:terrain_mesh_smoke_region_index");
    private static final KeyId TERRAIN_SMOKE_INDIRECT_ID = KeyId.of("sketch_vktest:terrain_mesh_smoke_indirect");
    private static final KeyId TERRAIN_SMOKE_CULL_COUNTER_ID = KeyId.of("sketch_vktest:terrain_mesh_smoke_cull_counter");
    private static final KeyId TERRAIN_SMOKE_ELEMENT_COUNTER_ID = KeyId.of("sketch_vktest:terrain_mesh_smoke_element_counter");
    private static final KeyId TERRAIN_SMOKE_READBACK_ID = KeyId.of("sketch_vktest:terrain_mesh_smoke_readback");
    private static final KeyId TERRAIN_BINDING_MESH = KeyId.of("chunk_section_mesh");
    private static final KeyId TERRAIN_BINDING_DRAW_COMMAND = KeyId.of("chunk_draw_command");
    private static final KeyId TERRAIN_BINDING_MESH_COUNTER = KeyId.of("mesh_counter");
    private static final KeyId TERRAIN_BINDING_REGION_POS = KeyId.of("region_pos");
    private static final KeyId TERRAIN_BINDING_MAX_ELEMENT = KeyId.of("max_element_count");
    private static final KeyId TERRAIN_BINDING_PERSISTENT_MAX = KeyId.of("persistent_max_element_count");

    private VulkanTestMain() {
    }

    public static void main(String[] args) {
        BackendKind backendKind = parseBackendKind(args);
        long smokeDurationMillis = parseSmokeDurationMillis(args);
        List<ScheduledResize> scheduledResizes = parseScheduledResizes(args);
        boolean terrainMeshSmoke = hasFlag(args, "--terrain-mesh-smoke");
        GLFWErrorCallback errorCallback = GLFWErrorCallback.createPrint(System.err);
        errorCallback.set();

        long window = MemoryUtil.NULL;
        PipelineTestScene scene = null;
        VkTestMainWindowControlSurface controlSurface = null;
        try {
            initializeGlfw(backendKind);
            boolean visible = !terrainMeshSmoke && (smokeDurationMillis <= 0L || !scheduledResizes.isEmpty());
            window = createWindow(backendKind, visible);
            GlfwWindowService windowService = new GlfwWindowService(window, backendKind == BackendKind.DESKTOP_GL);
            VkTestPresentationController presentationController = new VkTestPresentationController();

            if (backendKind == BackendKind.DESKTOP_GL) {
                GLFW.glfwMakeContextCurrent(window);
                GL.createCapabilities();
                GLFW.glfwSwapInterval(0);
                GraphicsDriver.registerBackendBootstrap(
                        new OpenGLBackendBootstrap(BackendKind.DESKTOP_GL, new OpenGLAPI()));
            } else {
                GraphicsDriver.registerBackendBootstrap(new VulkanBackendBootstrap());
            }

            GraphicsDriver.bootstrap(
                    backendKind,
                    new BackendBootstrapContext("sketch-vk-test", window, windowService, presentationController));
            GraphicsDriver.runtime().registerMainThread();
            EventBusBridge.setImplementation(new VkTestEventBusImplementation());

            if (terrainMeshSmoke) {
                runTerrainMeshSmoke(backendKind);
                return;
            }

            int[] framebufferSize = framebufferSize(window);
            scene = PipelineTestScene.create(backendKind, framebufferSize[0], framebufferSize[1]);
            scene.setRelaxAsyncValidation(!scheduledResizes.isEmpty());
            installResizeCallback(window, backendKind, scene, presentationController);
            boolean controlPlaneEnabled = smokeDurationMillis <= 0L && !terrainMeshSmoke;
            if (controlPlaneEnabled) {
                controlSurface = new VkTestMainWindowControlSurface(window, windowService, presentationController, backendKind, scene);
            }

            printBootInfo(backendKind);

            long deadline = smokeDurationMillis > 0L ? System.nanoTime() + smokeDurationMillis * 1_000_000L : Long.MAX_VALUE;
            long startTime = System.nanoTime();
            VulkanBackendRuntime vulkanRuntime =
                    backendKind == BackendKind.VULKAN ? (VulkanBackendRuntime) GraphicsDriver.runtime() : null;

            while (!GLFW.glfwWindowShouldClose(window)) {
                GLFW.glfwPollEvents();
                if (controlSurface != null) {
                    controlSurface.handleHotkeys();
                }
                if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
                    GLFW.glfwSetWindowShouldClose(window, true);
                }
                runScheduledResizes(window, scheduledResizes, startTime, vulkanRuntime);
                scene.renderFrame();
                boolean frameDrawn = true;
                if (backendKind == BackendKind.DESKTOP_GL) {
                    GLFW.glfwSwapBuffers(window);
                }
                if (vulkanRuntime != null) {
                    frameDrawn = vulkanRuntime.drawFrame();
                }
                if (frameDrawn) {
                    scene.submitAsyncHiZAfterFrame();
                } else {
                    scene.deferAsyncSubmissions(3);
                }
                if (controlSurface != null) {
                    controlSurface.updateTitle();
                }
                if (System.nanoTime() >= deadline) {
                    GLFW.glfwSetWindowShouldClose(window, true);
                }
            }

            printShutdownStats(vulkanRuntime);
        } finally {
            if (controlSurface != null) {
                controlSurface.close();
            }
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

    private static boolean hasFlag(String[] args, String flag) {
        if (args == null || flag == null || flag.isBlank()) {
            return false;
        }
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
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

    private static void runScheduledResizes(
            long window,
            List<ScheduledResize> schedules,
            long startTime,
            VulkanBackendRuntime vulkanRuntime) {
        if (schedules.isEmpty()) {
            return;
        }
        long elapsedMillis = (System.nanoTime() - startTime) / 1_000_000L;
        ScheduledResize pendingResize = null;
        for (ScheduledResize resize : schedules) {
            if (resize.applied && !resize.settled) {
                pendingResize = resize;
                break;
            }
        }
        if (pendingResize != null && !pendingResize.updateSettlement(vulkanRuntime)) {
            return;
        }
        for (ScheduledResize resize : schedules) {
            if (resize.applied) {
                continue;
            }
            if (elapsedMillis < resize.delayMillis) {
                return;
            }
            GLFW.glfwSetWindowSize(window, resize.width, resize.height);
            resize.markApplied(vulkanRuntime);
            System.out.println("  requestedResize=" + resize.width + "x" + resize.height + "@" + elapsedMillis + "ms");
            return;
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

    private static void installResizeCallback(
            long window,
            BackendKind backendKind,
            PipelineTestScene scene,
            PresentationController presentationController) {
        if (scene == null) {
            return;
        }
        GLFW.glfwSetFramebufferSizeCallback(window, (handle, width, height) -> {
            scene.resize(width, height);
            if (presentationController != null) {
                presentationController.notifyFramebufferResized(width, height);
            }
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

    private static void runTerrainMeshSmoke(BackendKind backendKind) {
        GraphicsResourceManager resourceManager = GraphicsResourceManager.getInstance();
        TerrainMeshResourceSet terrainResources = TerrainMeshResourceSet.getInstance();
        List<KeyId> registeredResourceIds = List.of(
                TERRAIN_SMOKE_MESH_BUFFER_ID,
                TERRAIN_SMOKE_REGION_INDEX_ID,
                TERRAIN_SMOKE_INDIRECT_ID,
                TERRAIN_SMOKE_CULL_COUNTER_ID,
                TERRAIN_SMOKE_ELEMENT_COUNTER_ID,
                TERRAIN_SMOKE_READBACK_ID);

        try {
            terrainResources.ensureCoreResources();
            BackendStorageBuffer meshDataBuffer = terrainResources.ensureMeshDataBuffer(64L, 2);
            if (backendKind == BackendKind.VULKAN) {
                terrainResources.onRegionCapacityChanged(4);
            }
            meshDataBuffer = terrainResources.ensureMeshDataBuffer(64L, 4);

            BackendStorageBuffer regionIndexBuffer = requireNonDisposed("regionIndexBuffer", terrainResources.regionIndexBuffer());
            TerrainMeshIndirectBuffer indirectBuffer = requireNonDisposed("indirectCommands", terrainResources.indirectCommands());
            TerrainMeshCounterBuffer cullCounter = requireNonDisposed("cullingCounter", terrainResources.cullingCounter());
            TerrainMeshCounterBuffer elementCounter = requireNonDisposed("elementCounter", terrainResources.elementCounter());
            TerrainMeshReadbackBuffer readbackBuffer = requireNonDisposed("maxElementReadbackBuffer", terrainResources.maxElementReadbackBuffer());
            requireNonDisposed("meshDataBuffer", meshDataBuffer);

            seedTerrainMeshBuffers(backendKind, meshDataBuffer, regionIndexBuffer, indirectBuffer, cullCounter, elementCounter, readbackBuffer);
            registerTerrainMeshBuiltIns(resourceManager, meshDataBuffer, regionIndexBuffer, indirectBuffer, cullCounter, elementCounter, readbackBuffer);
            installTerrainMeshBindingPlan();
            verifyTerrainMeshBindings();

            System.out.println("Terrain mesh smoke completed");
            System.out.println("  backend=" + backendKind);
            System.out.println("  meshCapacityBytes=" + meshDataBuffer.capacityBytes());
            System.out.println("  regionIndexCapacityBytes=" + regionIndexBuffer.capacityBytes());
            System.out.println("  indirectCommandCount=" + indirectBuffer.commandCount());
            System.out.println("  cullCounterCount=" + cullCounter.counterCount());
            System.out.println("  elementCounterCount=" + elementCounter.counterCount());
            System.out.println("  readbackCapacityBytes=" + readbackBuffer.getCapacity());
        } finally {
            for (KeyId resourceId : registeredResourceIds) {
                resourceManager.removeResource(ResourceTypes.STORAGE_BUFFER, resourceId);
            }
            terrainResources.disposeOwnedResources();
        }
    }

    private static void seedTerrainMeshBuffers(
            BackendKind backendKind,
            BackendStorageBuffer meshDataBuffer,
            BackendStorageBuffer regionIndexBuffer,
            TerrainMeshIndirectBuffer indirectBuffer,
            TerrainMeshCounterBuffer cullCounter,
            TerrainMeshCounterBuffer elementCounter,
            TerrainMeshReadbackBuffer readbackBuffer) {
        if (meshDataBuffer.memoryAddress() == 0L || regionIndexBuffer.memoryAddress() == 0L) {
            throw new IllegalStateException("Terrain mesh smoke requires mapped mesh and region buffers");
        }

        MemoryUtil.memSet(meshDataBuffer.memoryAddress(), 0, meshDataBuffer.capacityBytes());
        MemoryUtil.memPutInt(meshDataBuffer.memoryAddress(), 0x12345678);
        meshDataBuffer.position(0L);
        meshDataBuffer.upload();

        MemoryUtil.memSet(regionIndexBuffer.memoryAddress(), 0, regionIndexBuffer.capacityBytes());
        MemoryUtil.memPutInt(regionIndexBuffer.memoryAddress(), 42);
        regionIndexBuffer.position(0L);
        regionIndexBuffer.upload();

        int smokeCount = backendKind == BackendKind.VULKAN ? 4 : 1;
        indirectBuffer.ensureCommandCapacity(smokeCount);
        indirectBuffer.clear();
        indirectBuffer.addDrawElementsCommand(36, 1, 0, 0, 0);
        indirectBuffer.setCommandCount(1);
        indirectBuffer.uploadRange(0L, BackendIndirectBuffer.COMMAND_STRIDE_BYTES);

        if (backendKind == BackendKind.VULKAN) {
            cullCounter.resize(4);
            elementCounter.resize(4);
            readbackBuffer.ensureCapacity(4, true);
        }
        cullCounter.updateCount(3);
        elementCounter.updateCount(7);
    }

    private static void registerTerrainMeshBuiltIns(
            GraphicsResourceManager resourceManager,
            BackendStorageBuffer meshDataBuffer,
            BackendStorageBuffer regionIndexBuffer,
            TerrainMeshIndirectBuffer indirectBuffer,
            TerrainMeshCounterBuffer cullCounter,
            TerrainMeshCounterBuffer elementCounter,
            TerrainMeshReadbackBuffer readbackBuffer) {
        resourceManager.registerBuiltIn(ResourceTypes.STORAGE_BUFFER, TERRAIN_SMOKE_MESH_BUFFER_ID, () -> meshDataBuffer);
        resourceManager.registerBuiltIn(ResourceTypes.STORAGE_BUFFER, TERRAIN_SMOKE_REGION_INDEX_ID, () -> regionIndexBuffer);
        resourceManager.registerBuiltIn(ResourceTypes.STORAGE_BUFFER, TERRAIN_SMOKE_INDIRECT_ID, () -> indirectBuffer);
        resourceManager.registerBuiltIn(ResourceTypes.STORAGE_BUFFER, TERRAIN_SMOKE_CULL_COUNTER_ID, () -> cullCounter);
        resourceManager.registerBuiltIn(ResourceTypes.STORAGE_BUFFER, TERRAIN_SMOKE_ELEMENT_COUNTER_ID, () -> elementCounter);
        resourceManager.registerBuiltIn(ResourceTypes.STORAGE_BUFFER, TERRAIN_SMOKE_READBACK_ID, () -> readbackBuffer);
    }

    private static void installTerrainMeshBindingPlan() {
        ResourceBinding binding = new ResourceBinding();
        binding.addBinding(ResourceTypes.STORAGE_BUFFER, TERRAIN_BINDING_MESH, TERRAIN_SMOKE_MESH_BUFFER_ID);
        binding.addBinding(ResourceTypes.STORAGE_BUFFER, TERRAIN_BINDING_DRAW_COMMAND, TERRAIN_SMOKE_INDIRECT_ID);
        binding.addBinding(ResourceTypes.STORAGE_BUFFER, TERRAIN_BINDING_MESH_COUNTER, TERRAIN_SMOKE_CULL_COUNTER_ID);
        binding.addBinding(ResourceTypes.STORAGE_BUFFER, TERRAIN_BINDING_REGION_POS, TERRAIN_SMOKE_REGION_INDEX_ID);
        binding.addBinding(ResourceTypes.STORAGE_BUFFER, TERRAIN_BINDING_MAX_ELEMENT, TERRAIN_SMOKE_ELEMENT_COUNTER_ID);
        binding.addBinding(ResourceTypes.STORAGE_BUFFER, TERRAIN_BINDING_PERSISTENT_MAX, TERRAIN_SMOKE_READBACK_ID);
        ResourceBindingPlan bindingPlan = ResourceBindingPlan.from(binding);
        ResourceSetKey resourceSetKey = ResourceSetKey.from(bindingPlan, ResourceUniformSet.empty());
        FrameExecutionPlan.ResourceUploadPlan uploadPlan = new FrameExecutionPlan.ResourceUploadPlan(
                TERRAIN_SMOKE_STAGE_ID,
                resourceSetKey,
                bindingPlan,
                UniformGroupSet.empty(),
                TERRAIN_SMOKE_SHADER_ID,
                bindingPlan.layoutKey());
        GraphicsDriver.resourceAllocator().installExecutionPlan(
                new FrameExecutionPlan(java.util.Map.of(), java.util.List.of(), java.util.List.of(uploadPlan), null, null),
                1L,
                Math.max(1, GraphicsDriver.submissionScheduler().framesInFlight()));
    }

    private static void verifyTerrainMeshBindings() {
        verifyStorageBinding(TERRAIN_SMOKE_MESH_BUFFER_ID, 0);
        verifyStorageBinding(TERRAIN_SMOKE_INDIRECT_ID, 1);
        verifyStorageBinding(TERRAIN_SMOKE_CULL_COUNTER_ID, 2);
        verifyStorageBinding(TERRAIN_SMOKE_REGION_INDEX_ID, 3);
        verifyStorageBinding(TERRAIN_SMOKE_ELEMENT_COUNTER_ID, 4);
        verifyStorageBinding(TERRAIN_SMOKE_READBACK_ID, 5);
    }

    private static void verifyStorageBinding(KeyId resourceId, int binding) {
        BackendInstalledBindableResource bindable = GraphicsDriver.runtime()
                .resourceResolver()
                .resolveBindableResource(ResourceTypes.STORAGE_BUFFER, resourceId);
        if (bindable == null || bindable.isDisposed()) {
            throw new IllegalStateException("Missing storage-buffer binding for terrain mesh smoke resource " + resourceId);
        }
        bindable.bind(ResourceTypes.STORAGE_BUFFER, binding);
    }

    private static <T> T requireNonDisposed(String label, T resource) {
        if (!(resource instanceof rogo.sketch.core.api.ResourceObject resourceObject) || resourceObject.isDisposed()) {
            throw new IllegalStateException("Terrain mesh smoke missing live resource: " + label);
        }
        return resource;
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
        private boolean settled;
        private boolean validationObserved;
        private long appliedRenderedFrame = -1L;
        private long appliedSwapchainGeneration = -1L;
        private long validationCompletedFrame = -1L;

        private ScheduledResize(int width, int height, long delayMillis) {
            this.width = width;
            this.height = height;
            this.delayMillis = delayMillis;
        }

        private void markApplied(VulkanBackendRuntime vulkanRuntime) {
            applied = true;
            if (vulkanRuntime == null) {
                settled = true;
                return;
            }
            appliedRenderedFrame = vulkanRuntime.renderedFrameCount();
            appliedSwapchainGeneration = vulkanRuntime.swapchainGeneration();
        }

        private boolean updateSettlement(VulkanBackendRuntime vulkanRuntime) {
            if (!applied || settled || vulkanRuntime == null) {
                return settled;
            }
            if (!validationObserved) {
                validationObserved = vulkanRuntime.swapchainGeneration() > appliedSwapchainGeneration
                        || vulkanRuntime.postResizeValidationActive();
                if (!validationObserved) {
                    return false;
                }
            }
            if (vulkanRuntime.postResizeValidationActive()) {
                return false;
            }
            if (validationCompletedFrame < 0L) {
                validationCompletedFrame = vulkanRuntime.renderedFrameCount();
                return false;
            }
            if (vulkanRuntime.renderedFrameCount() <= validationCompletedFrame
                    || vulkanRuntime.renderedFrameCount() <= appliedRenderedFrame) {
                return false;
            }
            settled = true;
            System.out.println("  resizeSettled=" + width + "x" + height
                    + " frame=" + vulkanRuntime.renderedFrameCount()
                    + " swapchainGeneration=" + vulkanRuntime.swapchainGeneration());
            return true;
        }
    }

    private static final class VkTestMainWindowControlSurface implements AutoCloseable {
        private final long windowHandle;
        private final WindowService windowService;
        private final PresentationController presentationController;
        private final BackendKind backendKind;
        private final PipelineTestScene scene;
        private final String baseTitle;
        private boolean visible;
        private boolean f7Down;
        private boolean f8Down;
        private boolean f9Down;
        private boolean f10Down;
        private boolean leftBracketDown;
        private boolean rightBracketDown;
        private volatile String profilerStatus = "IDLE";

        private VkTestMainWindowControlSurface(
                long windowHandle,
                WindowService windowService,
                PresentationController presentationController,
                BackendKind backendKind,
                PipelineTestScene scene) {
            this.windowHandle = windowHandle;
            this.windowService = windowService;
            this.presentationController = presentationController;
            this.backendKind = backendKind;
            this.scene = scene;
            this.baseTitle = backendKind == BackendKind.VULKAN ? "Sketch Vulkan Test" : "Sketch Desktop GL Test";
            GLFW.glfwSetWindowTitle(windowHandle, baseTitle);
        }

        private void handleHotkeys() {
            boolean f7Pressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_F7) == GLFW.GLFW_PRESS;
            if (f7Pressed && !f7Down) {
                triggerProfilerCapture();
                updateTitle();
            }
            f7Down = f7Pressed;

            boolean f8Pressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_F8) == GLFW.GLFW_PRESS;
            if (f8Pressed && !f8Down) {
                visible = !visible;
                updateTitle();
            }
            f8Down = f8Pressed;
            if (!visible) {
                return;
            }

            boolean f9Pressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_F9) == GLFW.GLFW_PRESS;
            if (f9Pressed && !f9Down) {
                scene.prepareForWindowMutation();
                windowService.setVSync(!windowService.vSyncEnabled());
                presentationController.applyWindowSettings(windowService);
                updateTitle();
            }
            f9Down = f9Pressed;

            boolean f10Pressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_F10) == GLFW.GLFW_PRESS;
            if (f10Pressed && !f10Down) {
                scene.prepareForWindowMutation();
                windowService.setFullscreen(!windowService.fullscreenEnabled());
                presentationController.applyWindowSettings(windowService);
                updateTitle();
            }
            f10Down = f10Pressed;

            boolean leftPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_BRACKET) == GLFW.GLFW_PRESS;
            if (leftPressed && !leftBracketDown) {
                cycleResolution(-1);
            }
            leftBracketDown = leftPressed;

            boolean rightPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_BRACKET) == GLFW.GLFW_PRESS;
            if (rightPressed && !rightBracketDown) {
                cycleResolution(1);
            }
            rightBracketDown = rightPressed;
        }

        private void cycleResolution(int delta) {
            List<WindowDisplayMode> modes = windowService.supportedDisplayModes();
            if (modes.isEmpty()) {
                return;
            }
            scene.prepareForWindowMutation();
            int currentWidth = windowService.framebufferWidth();
            int currentHeight = windowService.framebufferHeight();
            int index = 0;
            int bestDistance = Integer.MAX_VALUE;
            for (int i = 0; i < modes.size(); i++) {
                WindowDisplayMode mode = modes.get(i);
                int distance = Math.abs(mode.width() - currentWidth) + Math.abs(mode.height() - currentHeight);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    index = i;
                }
            }
            int next = Math.floorMod(index + delta, modes.size());
            windowService.applyDisplayMode(modes.get(next));
            presentationController.applyWindowSettings(windowService);
            updateTitle();
        }

        private void updateTitle() {
            if (!visible) {
                GLFW.glfwSetWindowTitle(windowHandle, baseTitle);
                return;
            }
            PipelineTestScene.ControlSnapshot snapshot = scene.captureControlSnapshot();
            MemoryDebugSnapshot memorySnapshot = UnifiedMemoryFabric.get().snapshot();
            BackendCapabilities capabilities = GraphicsDriver.capabilities();
            String title = baseTitle
                    + " | F8 Controls"
                    + " | F7 Profiler:" + profilerStatus
                    + " | F9 VSync:" + (windowService.vSyncEnabled() ? "ON" : "OFF")
                    + " | F10 Fullscreen:" + (windowService.fullscreenEnabled() ? "ON" : "OFF")
                    + " | [ ] Resolution:" + snapshot.windowWidth() + "x" + snapshot.windowHeight()
                    + " | Async C/G:" + snapshot.asyncHiZSubmissionCount() + "/" + snapshot.asyncGraphicsSubmissionCount()
                    + " | HiZ src/snap/lin/hiz:" + snapshot.hizSourceWidth() + "x" + snapshot.hizSourceHeight()
                    + "/" + snapshot.hizSnapshotWidth() + "x" + snapshot.hizSnapshotHeight()
                    + "/" + snapshot.hizSnapshotWidth() + "x" + snapshot.hizSnapshotHeight()
                    + "/" + snapshot.hizPublishedWidth() + "x" + snapshot.hizPublishedHeight()
                    + " | epochs " + snapshot.hizSubmittedEpoch() + "->" + snapshot.hizPublishedEpoch()
                    + " | offscreen=" + capabilities.offscreenGraphicsWorkerSupported()
                    + " | memLive=" + DashboardMemorySectionBuilder.formatBytes(memorySnapshot.totalLiveBytes());
            GLFW.glfwSetWindowTitle(windowHandle, title);
        }

        private void triggerProfilerCapture() {
            if (SimpleProfiler.get().isRecording()) {
                profilerStatus = "REC";
                return;
            }
            profilerStatus = "REC";
            SimpleProfiler.get().startRecording(5.0, file -> {
                profilerStatus = file != null ? file.getName() : "SAVE_FAIL";
                if (file != null) {
                    SketchDiagnostics.get().info("vk-test-profiler", "Saved SimpleProfiler trace to " + file.getAbsolutePath());
                } else {
                    SketchDiagnostics.get().warn("vk-test-profiler", "SimpleProfiler capture finished without an output file");
                }
            });
        }

        @Override
        public void close() {
            GLFW.glfwSetWindowTitle(windowHandle, baseTitle);
        }
    }

    private static final class GlfwWindowService implements WindowService {
        private final long windowHandle;
        private final boolean glfwSwapControlled;
        private volatile boolean vSyncEnabled = false;
        private volatile boolean fullscreenEnabled;
        private volatile int windowedX = 80;
        private volatile int windowedY = 80;
        private volatile int windowedWidth = 1280;
        private volatile int windowedHeight = 720;

        private GlfwWindowService(long windowHandle, boolean glfwSwapControlled) {
            this.windowHandle = windowHandle;
            this.glfwSwapControlled = glfwSwapControlled;
            this.fullscreenEnabled = GLFW.glfwGetWindowMonitor(windowHandle) != MemoryUtil.NULL;
            rememberWindowedPlacement();
        }

        @Override
        public long windowHandle() {
            return windowHandle;
        }

        @Override
        public int framebufferWidth() {
            return framebufferSize(windowHandle)[0];
        }

        @Override
        public int framebufferHeight() {
            return framebufferSize(windowHandle)[1];
        }

        @Override
        public void resizeFramebuffer(int width, int height) {
            applyWindowedSize(width, height);
        }

        @Override
        public void applyWindowedSize(int width, int height) {
            int clampedWidth = Math.max(1, width);
            int clampedHeight = Math.max(1, height);
            windowedWidth = clampedWidth;
            windowedHeight = clampedHeight;
            if (fullscreenEnabled) {
                long monitor = GLFW.glfwGetPrimaryMonitor();
                GLFWVidMode mode = monitor != MemoryUtil.NULL ? GLFW.glfwGetVideoMode(monitor) : null;
                GLFW.glfwSetWindowMonitor(
                        windowHandle,
                        monitor,
                        0,
                        0,
                        clampedWidth,
                        clampedHeight,
                        mode != null ? mode.refreshRate() : GLFW.GLFW_DONT_CARE);
            } else {
                GLFW.glfwSetWindowSize(windowHandle, clampedWidth, clampedHeight);
            }
        }

        @Override
        public void setVSync(boolean enabled) {
            vSyncEnabled = enabled;
            if (glfwSwapControlled) {
                GLFW.glfwSwapInterval(enabled ? 1 : 0);
            }
        }

        @Override
        public boolean vSyncEnabled() {
            return vSyncEnabled;
        }

        @Override
        public List<WindowDisplayMode> supportedDisplayModes() {
            long monitor = GLFW.glfwGetPrimaryMonitor();
            if (monitor == MemoryUtil.NULL) {
                return WindowService.super.supportedDisplayModes();
            }
            GLFWVidMode.Buffer modes = GLFW.glfwGetVideoModes(monitor);
            if (modes == null || !modes.hasRemaining()) {
                return WindowService.super.supportedDisplayModes();
            }
            LinkedHashMap<String, WindowDisplayMode> uniqueModes = new LinkedHashMap<>();
            for (int i = 0; i < modes.limit(); i++) {
                GLFWVidMode mode = modes.get(i);
                WindowDisplayMode displayMode = new WindowDisplayMode(mode.width(), mode.height(), mode.refreshRate());
                uniqueModes.putIfAbsent(displayMode.width() + "x" + displayMode.height(), displayMode);
            }
            return List.copyOf(uniqueModes.values());
        }

        @Override
        public boolean fullscreenEnabled() {
            return fullscreenEnabled;
        }

        @Override
        public void setFullscreen(boolean enabled) {
            if (enabled == fullscreenEnabled) {
                return;
            }
            if (enabled) {
                rememberWindowedPlacement();
                long monitor = GLFW.glfwGetPrimaryMonitor();
                GLFWVidMode mode = monitor != MemoryUtil.NULL ? GLFW.glfwGetVideoMode(monitor) : null;
                if (monitor != MemoryUtil.NULL && mode != null) {
                    GLFW.glfwSetWindowMonitor(
                            windowHandle,
                            monitor,
                            0,
                            0,
                            mode.width(),
                            mode.height(),
                            mode.refreshRate());
                }
            } else {
                GLFW.glfwSetWindowMonitor(
                        windowHandle,
                        MemoryUtil.NULL,
                        windowedX,
                        windowedY,
                        Math.max(1, windowedWidth),
                        Math.max(1, windowedHeight),
                        GLFW.GLFW_DONT_CARE);
            }
            fullscreenEnabled = enabled;
            if (glfwSwapControlled) {
                GLFW.glfwSwapInterval(vSyncEnabled ? 1 : 0);
            }
        }

        @Override
        public void applyDisplayMode(WindowDisplayMode mode) {
            if (mode == null) {
                return;
            }
            if (fullscreenEnabled) {
                long monitor = GLFW.glfwGetPrimaryMonitor();
                GLFW.glfwSetWindowMonitor(
                        windowHandle,
                        monitor,
                        0,
                        0,
                        mode.width(),
                        mode.height(),
                        mode.refreshRate() > 0 ? mode.refreshRate() : GLFW.GLFW_DONT_CARE);
            } else {
                applyWindowedSize(mode.width(), mode.height());
            }
        }

        private void rememberWindowedPlacement() {
            if (fullscreenEnabled) {
                return;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer x = stack.mallocInt(1);
                IntBuffer y = stack.mallocInt(1);
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                GLFW.glfwGetWindowPos(windowHandle, x, y);
                GLFW.glfwGetWindowSize(windowHandle, width, height);
                windowedX = x.get(0);
                windowedY = y.get(0);
                windowedWidth = Math.max(1, width.get(0));
                windowedHeight = Math.max(1, height.get(0));
            }
        }
    }

    private static final class VkTestPresentationController implements PresentationController {
        @Override
        public void notifyFramebufferResized(int width, int height) {
        }

        @Override
        public void applyWindowSettings(WindowService windowService) {
            if (GraphicsDriver.runtime() instanceof VulkanBackendRuntime runtime) {
                runtime.setVSyncEnabled(windowService.vSyncEnabled());
            }
        }
    }
}

