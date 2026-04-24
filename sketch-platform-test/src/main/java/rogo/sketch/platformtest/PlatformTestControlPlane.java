package rogo.sketch.platformtest;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.type.ImBoolean;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.backend.BackendCapabilities;
import rogo.sketch.core.backend.BackendKind;
import rogo.sketch.core.backend.PresentationController;
import rogo.sketch.core.backend.WindowDisplayMode;
import rogo.sketch.core.backend.WindowService;
import rogo.sketch.core.dashboard.DashboardMemorySectionBuilder;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.memory.MemoryDebugSnapshot;
import rogo.sketch.core.memory.MemoryDomainSnapshot;
import rogo.sketch.core.memory.UnifiedMemoryFabric;

import java.util.List;

final class PlatformTestControlPlane implements AutoCloseable {
    private static final int CONTROL_WINDOW_WIDTH = 980;
    private static final int CONTROL_WINDOW_HEIGHT = 760;
    private static final String GLSL_VERSION = "#version 330 core";

    private final WindowService windowService;
    private final PresentationController presentationController;
    private final BackendKind backendKind;
    private final PipelineTestScene scene;
    private long windowHandle;
    private boolean visible;
    private boolean initialized;
    private boolean f8Down;
    private GLCapabilities controlCapabilities;
    private ImGuiImplGlfw imguiGlfw;
    private ImGuiImplGl3 imguiGl3;
    private Boolean pendingFullscreen;
    private Boolean pendingVSync;
    private WindowDisplayMode pendingDisplayMode;

    PlatformTestControlPlane(
            WindowService windowService,
            PresentationController presentationController,
            BackendKind backendKind,
            PipelineTestScene scene) {
        this.windowService = windowService;
        this.presentationController = presentationController;
        this.backendKind = backendKind;
        this.scene = scene;
    }

    void handleHotkeys(long mainWindowHandle) {
        boolean pressed = GLFW.glfwGetKey(mainWindowHandle, GLFW.GLFW_KEY_F8) == GLFW.GLFW_PRESS;
        if (pressed && !f8Down) {
            toggle();
        }
        f8Down = pressed;
    }

    void render(long mainWindowHandle, GLCapabilities mainWindowCapabilities) {
        if (!visible) {
            return;
        }
        ensureInitialized();
        if (windowHandle == MemoryUtil.NULL) {
            return;
        }
        if (GLFW.glfwWindowShouldClose(windowHandle)) {
            closeControlWindow();
            return;
        }

        long previousContext = GLFW.glfwGetCurrentContext();
        GLFW.glfwMakeContextCurrent(windowHandle);
        GL.setCapabilities(controlCapabilities);

        imguiGlfw.newFrame();
        syncInput();
        ImGui.newFrame();
        drawPanels();
        ImGui.render();

        int[] framebufferSize = controlFramebufferSize();
        GL11.glViewport(0, 0, framebufferSize[0], framebufferSize[1]);
        GL11.glClearColor(0.08f, 0.10f, 0.13f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        imguiGl3.renderDrawData(ImGui.getDrawData());
        GLFW.glfwSwapBuffers(windowHandle);

        if (previousContext != MemoryUtil.NULL) {
            GLFW.glfwMakeContextCurrent(previousContext);
            if (mainWindowCapabilities != null) {
                GL.setCapabilities(mainWindowCapabilities);
            }
        } else {
            GLFW.glfwMakeContextCurrent(MemoryUtil.NULL);
        }
        applyPendingWindowActions();
    }

    private void toggle() {
        ensureInitialized();
        visible = !visible;
        if (windowHandle != MemoryUtil.NULL) {
            if (visible) {
                GLFW.glfwShowWindow(windowHandle);
                GLFW.glfwFocusWindow(windowHandle);
            } else {
                GLFW.glfwHideWindow(windowHandle);
            }
        }
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        windowHandle = GLFW.glfwCreateWindow(
                CONTROL_WINDOW_WIDTH,
                CONTROL_WINDOW_HEIGHT,
                "Sketch platform-test Control Plane",
                MemoryUtil.NULL,
                MemoryUtil.NULL);
        if (windowHandle == MemoryUtil.NULL) {
            throw new IllegalStateException("Failed to create platform-test control plane window");
        }

        GLFW.glfwMakeContextCurrent(windowHandle);
        controlCapabilities = GL.createCapabilities();
        GLFW.glfwSwapInterval(0);
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        imguiGlfw = new ImGuiImplGlfw();
        imguiGlfw.init(windowHandle, false);
        imguiGl3 = new ImGuiImplGl3();
        imguiGl3.init(GLSL_VERSION);
        GLFW.glfwHideWindow(windowHandle);
        GLFW.glfwMakeContextCurrent(MemoryUtil.NULL);
    }

    private void syncInput() {
        ImGuiIO io = ImGui.getIO();
        int[] framebufferSize = controlFramebufferSize();
        io.setDisplaySize(framebufferSize[0], framebufferSize[1]);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            var x = stack.mallocDouble(1);
            var y = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(windowHandle, x, y);
            io.setMousePos((float) x.get(0), (float) y.get(0));
        }

        io.setMouseDown(0, GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS);
        io.setMouseDown(1, GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS);
        io.setMouseDown(2, GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS);
    }

    private int[] controlFramebufferSize() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var width = stack.mallocInt(1);
            var height = stack.mallocInt(1);
            GLFW.glfwGetFramebufferSize(windowHandle, width, height);
            return new int[]{Math.max(1, width.get(0)), Math.max(1, height.get(0))};
        }
    }

    private void applyPendingWindowActions() {
        boolean changed = false;
        if (pendingVSync != null) {
            windowService.setVSync(pendingVSync);
            pendingVSync = null;
            changed = true;
        }
        if (pendingFullscreen != null) {
            windowService.setFullscreen(pendingFullscreen);
            pendingFullscreen = null;
            changed = true;
        }
        if (pendingDisplayMode != null) {
            windowService.applyDisplayMode(pendingDisplayMode);
            pendingDisplayMode = null;
            changed = true;
        }
        if (changed) {
            presentationController.applyWindowSettings(windowService);
        }
    }

    private void drawPanels() {
        PipelineTestScene.ControlSnapshot snapshot = scene.captureControlSnapshot();
        MemoryDebugSnapshot memorySnapshot = UnifiedMemoryFabric.get().snapshot();
        BackendCapabilities capabilities = GraphicsDriver.capabilities();

        ImGui.setNextWindowSize(940, 720, ImGuiCond.FirstUseEver);
        ImGui.begin("platform-test Control Plane", ImGuiWindowFlags.NoCollapse);
        ImGui.text("Backend: " + backendKind + " / " + GraphicsDriver.runtime().backendName());
        ImGui.text("Framebuffer: " + snapshot.windowWidth() + "x" + snapshot.windowHeight());
        ImGui.text("Capabilities: upload=" + capabilities.uploadWorkerSupported()
                + ", compute=" + capabilities.computeWorkerSupported()
                + ", offscreen=" + capabilities.offscreenGraphicsWorkerSupported());
        ImGui.separator();

        ImBoolean fullScreen = new ImBoolean(windowService.fullscreenEnabled());
        if (ImGui.checkbox("Fullscreen", fullScreen)) {
            pendingFullscreen = fullScreen.get();
        }

        ImBoolean vSync = new ImBoolean(windowService.vSyncEnabled());
        if (ImGui.checkbox("V-Sync", vSync)) {
            pendingVSync = vSync.get();
        }

        List<WindowDisplayMode> displayModes = windowService.supportedDisplayModes();
        WindowDisplayMode currentMode = new WindowDisplayMode(snapshot.windowWidth(), snapshot.windowHeight(), 0);
        if (ImGui.beginCombo("Resolution", currentMode.label())) {
            for (WindowDisplayMode mode : displayModes) {
                boolean selected = mode.width() == currentMode.width() && mode.height() == currentMode.height();
                if (ImGui.selectable(mode.label(), selected)) {
                    pendingDisplayMode = mode;
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }

        ImGui.separator();
        ImGui.text("Async Compute Jobs: submitted=" + snapshot.asyncHiZSubmissionCount()
                + ", completed=" + snapshot.asyncHiZCompletionCount()
                + ", pending=" + snapshot.hizPendingJob());
        ImGui.text("Async Graphics Jobs: submitted=" + snapshot.asyncGraphicsSubmissionCount()
                + ", completed=" + snapshot.asyncGraphicsCompletionCount()
                + ", pending=" + snapshot.asyncGraphicsPendingJob());
        ImGui.separator();

        if (snapshot.sceneMode() == PipelineTestScene.SceneMode.REAL_SCENE) {
            ImGui.text("Real Scene");
            ImGui.bulletText("Scene asset: " + snapshot.sceneAsset());
            ImGui.bulletText("Actor model: " + snapshot.actorModel());
            ImGui.bulletText("Camera: " + formatVector(snapshot.cameraPosition()));
            ImGui.bulletText("Actor: " + formatVector(snapshot.actorPosition()));
            ImGui.bulletText("Shadow target actor packets: " + snapshot.shadowTargetPacketCount());
            ImGui.bulletText("Shadow debug: " + snapshot.shadowDebugMode().displayName());
            ImGui.bulletText("Shadow map: " + snapshot.shadowMapWidth() + "x" + snapshot.shadowMapHeight()
                    + " @ epoch " + snapshot.shadowEpoch());
            ImGui.bulletText("Shadow debug overlay packets: " + snapshot.shadowDebugOverlayPacketCount());
            ImGui.textWrapped("WASD moves the actor in noclip mode. Hold right mouse and drag to steer the third-person camera; Shift increases movement speed.");
        } else {
            ImGui.text("HiZ Inspection");
            ImGui.bulletText("Source depth (pre-freeze): " + snapshot.hizSourceWidth() + "x" + snapshot.hizSourceHeight());
            ImGui.bulletText("HiZ input snapshot (pre-compute): " + snapshot.hizSnapshotWidth() + "x" + snapshot.hizSnapshotHeight());
            ImGui.bulletText("HiZ input linearized preview: " + snapshot.hizSnapshotWidth() + "x" + snapshot.hizSnapshotHeight());
            ImGui.bulletText("Published HiZ (post-compute): " + snapshot.hizPublishedWidth() + "x" + snapshot.hizPublishedHeight());
            ImGui.bulletText("Submitted epoch: " + snapshot.hizSubmittedEpoch());
            ImGui.bulletText("Published epoch: " + snapshot.hizPublishedEpoch());
            ImGui.bulletText("Async offscreen: " + snapshot.asyncGraphicsWidth() + "x" + snapshot.asyncGraphicsHeight()
                    + " @ epoch " + snapshot.asyncGraphicsPublishedEpoch());
            ImGui.textWrapped("The main platform-test window shows the live inspection strip for source depth, frozen snapshot, linearized snapshot preview, published HiZ, and async offscreen color.");
        }
        ImGui.separator();

        ImGui.text("Memory");
        ImGui.bulletText("Live: " + DashboardMemorySectionBuilder.formatBytes(memorySnapshot.totalLiveBytes()));
        ImGui.bulletText("Reserved: " + DashboardMemorySectionBuilder.formatBytes(memorySnapshot.totalReservedBytes()));
        ImGui.bulletText("Peak: " + DashboardMemorySectionBuilder.formatBytes(memorySnapshot.totalPeakBytes()));
        for (MemoryDomainSnapshot domainSnapshot : memorySnapshot.domains()) {
            ImGui.bulletText(domainSnapshot.domain().name()
                    + ": live=" + DashboardMemorySectionBuilder.formatBytes(domainSnapshot.liveBytes())
                    + ", reserved=" + DashboardMemorySectionBuilder.formatBytes(domainSnapshot.reservedBytes()));
        }

        ImGui.end();
    }

    private static String formatVector(Vector3f vector) {
        if (vector == null) {
            return "(0.00, 0.00, 0.00)";
        }
        return "(%.2f, %.2f, %.2f)".formatted(vector.x, vector.y, vector.z);
    }

    private void closeControlWindow() {
        visible = false;
        if (imguiGl3 != null) {
            imguiGl3.dispose();
            imguiGl3 = null;
        }
        if (imguiGlfw != null) {
            imguiGlfw.dispose();
            imguiGlfw = null;
        }
        if (windowHandle != MemoryUtil.NULL) {
            GLFW.glfwDestroyWindow(windowHandle);
            windowHandle = MemoryUtil.NULL;
        }
        ImGui.destroyContext();
        controlCapabilities = null;
        initialized = false;
    }

    @Override
    public void close() {
        closeControlWindow();
    }
}
