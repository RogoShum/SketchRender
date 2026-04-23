package rogo.sketch.gui.debugui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import rogo.sketch.Config;
import rogo.sketch.core.dashboard.DashboardPrimitive;
import rogo.sketch.core.dashboard.DashboardViewModelFactory;
import rogo.sketch.core.dashboard.DashboardViewSceneBuilder;
import rogo.sketch.core.dashboard.DashboardViewSnapshot;
import rogo.sketch.core.debugger.DashboardController;
import rogo.sketch.core.debugger.DashboardDockResizeHandle;
import rogo.sketch.core.debugger.DashboardDataSource;
import rogo.sketch.core.debugger.DashboardDockSlotId;
import rogo.sketch.core.debugger.DashboardDockSlotSpec;
import rogo.sketch.core.debugger.DashboardPanelId;
import rogo.sketch.core.debugger.DashboardPanelMode;
import rogo.sketch.core.debugger.DashboardTab;
import rogo.sketch.core.debugger.DashboardTreeNode;
import rogo.sketch.core.debugger.DashboardWindowId;
import rogo.sketch.core.debugger.DashboardWorkspaceLayout;
import rogo.sketch.core.debugger.DashboardWorkspaceProfile;
import rogo.sketch.core.debugger.DashboardWorkspaceProfiles;
import rogo.sketch.core.debugger.ui.UiNodeType;
import rogo.sketch.core.pipeline.module.diagnostic.DiagnosticLevel;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeHost;
import rogo.sketch.core.ui.control.ChoiceSpec;
import rogo.sketch.core.ui.control.ControlSpec;
import rogo.sketch.core.ui.control.NumericKind;
import rogo.sketch.core.ui.control.NumericSpec;
import rogo.sketch.core.ui.frame.UiFrame;
import rogo.sketch.core.ui.frame.UiPrimitive;
import rogo.sketch.core.ui.geometry.UiRect;
import rogo.sketch.core.ui.geometry.UiScaleContext;
import rogo.sketch.vanilla.PipelineUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class AdaptiveDebugDashboardScreen extends Screen {
    private static long sessionAcknowledgedAlertSequence;

    private final DashboardController controller = new DashboardController();
    private final DashboardViewModelFactory modelFactory = new DashboardViewModelFactory();
    private final DashboardWorkspaceProfile workspaceProfile;
    private final DashboardViewSceneBuilder frameBuilder;
    private final AdaptiveDashboardRenderer renderer = new AdaptiveDashboardRenderer();
    private final ArrayDeque<Double> frameHistory = new ArrayDeque<>();
    private DashboardDataSource dataSource;
    private DashboardViewSnapshot latestSnapshot;
    private UiFrame latestFrame;
    private String draggingSliderControlId;
    private String draggingScrollArea;
    private int draggingScrollGrabOffset;
    private boolean draggingScrollHorizontal;
    private DashboardPanelId draggingPanelId;
    private DashboardWindowId draggingWindowId;
    private DashboardPanelId draggingWindowSourcePanelId;
    private double draggingWindowStartX;
    private double draggingWindowStartY;
    private UiRect draggingPanelBoundsStart;
    private int draggingPanelGrabOffsetX;
    private int draggingPanelGrabOffsetY;
    private DashboardPanelId resizingPanelId;
    private ResizeEdge resizingEdge = ResizeEdge.NONE;
    private UiRect resizingPanelBoundsStart;
    private DashboardDockSlotId resizingSlotId;
    private ResizeEdge resizingSlotEdge = ResizeEdge.NONE;
    private boolean expandedDefaults;
    private boolean panelLayoutLoaded;
    private long lastFrameSampleNanos;
    private float dashboardScale = 1.0f;
    private UiScaleContext scaleContext = UiScaleContext.of(1.0f, 1, 1);
    private String editingNumberControlId;
    private String editingNumberDraft = "";
    private Object editingNumberOriginalValue;

    public AdaptiveDebugDashboardScreen(Component title) {
        this(title, DashboardWorkspaceProfiles.dashboardDefault());
    }

    public AdaptiveDebugDashboardScreen(Component title, DashboardWorkspaceProfile workspaceProfile) {
        super(title);
        this.workspaceProfile = Objects.requireNonNullElse(workspaceProfile, DashboardWorkspaceProfiles.dashboardDefault());
        this.frameBuilder = new DashboardViewSceneBuilder(this.workspaceProfile, new MinecraftUiTextMetrics());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        savePanelLayout();
        super.onClose();
    }

    @Override
    public void removed() {
        savePanelLayout();
        super.removed();
    }

    @Override
    protected void init() {
        ModuleRuntimeHost runtimeHost = PipelineUtil.pipeline().runtimeHost();
        if (runtimeHost == null) {
            onClose();
            return;
        }
        dataSource = new AdaptiveDashboardDataSource(runtimeHost, this::frameHistorySnapshot);
        controller.acknowledgeAlertsUpTo(sessionAcknowledgedAlertSequence);
        loadPanelLayout();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (dataSource == null) {
            init();
            if (dataSource == null) {
                return;
            }
        }

        sampleFrameTime();
        dashboardScale = computeDashboardScale();
        scaleContext = UiScaleContext.of(dashboardScale, width, height);
        ensurePanelLayoutState();
        latestSnapshot = modelFactory.build(dataSource, PipelineUtil.pipeline().metricSnapshot(), PipelineUtil.pipeline().diagnosticsSnapshot());
        acknowledgeVisibleAlerts();
        if (!expandedDefaults) {
            restoreTreeExpansion(latestSnapshot);
            expandedDefaults = true;
        }
        latestFrame = frameBuilder.build(latestSnapshot, controller, scaleContext, editingNumberControlId, editingNumberDraft);
        if (clampScrolls()) {
            latestFrame = frameBuilder.build(latestSnapshot, controller, scaleContext, editingNumberControlId, editingNumberDraft);
        }
        AdaptiveMinecraftUiCanvas canvas = new AdaptiveMinecraftUiCanvas(guiGraphics);
        canvas.beginFrame(scaleContext);
        try {
            renderer.render(latestFrame, controller, canvas, logicalMouseX(mouseX), logicalMouseY(mouseY),
                    resizingPanelId, stringPropForEdge(resizingEdge),
                    resizingSlotId != null ? resizingSlotId.value() : null, stringPropForEdge(resizingSlotEdge));
        } finally {
            canvas.endFrame();
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (latestFrame == null || dataSource == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        double logicalMouseX = logicalMouseX(mouseX);
        double logicalMouseY = logicalMouseY(mouseY);

        DashboardPrimitive hoveredNode = renderer.findTopPrimitive(latestFrame, logicalMouseX, logicalMouseY);
        if (controller.openTopbarMenuId() != null && !isTopbarPrimitive(hoveredNode)) {
            controller.setOpenTopbarMenuId(null);
        }
        if (controller.openChoiceControlId() != null && !isChoicePopupPrimitive(hoveredNode) && !isChoiceOwnerPrimitive(hoveredNode)) {
            controller.closeChoiceDropdown();
        }
        if (editingNumberControlId != null) {
            DashboardPrimitive editingNode = findControlNode(editingNumberControlId);
            boolean editingHit = editingNode != null && editingNode.bounds().contains(logicalMouseX, logicalMouseY);
            if (!editingHit) {
                commitNumberEdit();
            }
        }

        if (hoveredNode == null) {
            if (controller.dockPreviewPanelId() != null) {
                controller.clearDockPreview();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (controller.dockPreviewPanelId() != null && shouldCancelDockSelection(hoveredNode)) {
            controller.clearDockPreview();
        }

        DashboardPanelId panelId = panelIdOf(hoveredNode);
        if (panelId != null && controller.isFloating(panelId)) {
            controller.focusPanel(panelId);
        }

        switch (hoveredNode.type()) {
            case TOPBAR -> {
                return handleTopbarClick(hoveredNode);
            }
            case TOPBAR_MENU_ITEM -> {
                return handleTopbarMenuItemClick(hoveredNode);
            }
            case POPUP_MENU_ITEM -> {
                return handlePopupMenuItemClick(hoveredNode);
            }
            case POPUP_PANEL -> {
                return true;
            }
            case WINDOW_TAB -> {
                return handleWindowTabClick(hoveredNode, button, mouseX, mouseY);
            }
            case PANEL -> {
                return handlePanelClick(hoveredNode, logicalMouseX, logicalMouseY, button);
            }
            case HEADER, DIAGNOSTIC_HEADER -> {
                return beginPanelDrag(hoveredNode, logicalMouseX, logicalMouseY, button);
            }
            case TAB_BUTTON -> {
                controller.setActiveTab(hoveredNode.id().equals("tab-settings") ? DashboardTab.MOD_SETTINGS : DashboardTab.SHADER_MACROS);
                return true;
            }
            case TREE_GROUP -> {
                controller.toggleExpanded(hoveredNode.id());
                savePanelLayout();
                return true;
            }
            case TREE_CONTROL -> {
                return handleControlClick(hoveredNode, logicalMouseX, logicalMouseY);
            }
            case MACRO_SECTION_HEADER -> {
                String sectionId = String.valueOf(hoveredNode.props().get("sectionId"));
                if ("memory".equals(sectionId)) {
                    controller.toggleMemorySectionExpanded();
                } else {
                    controller.toggleMacroConstantsExpanded();
                }
                savePanelLayout();
                return true;
            }
            case METRICS_LAYOUT_TOGGLE -> {
                controller.cycleMetricsLayoutMode();
                return true;
            }
            case CAPTURE_BUTTON -> {
                dataSource.requestFrameCapture();
                return true;
            }
            case DIAGNOSTIC_FILTER -> {
                controller.toggleDiagnosticFilter(DiagnosticLevel.valueOf(String.valueOf(hoveredNode.props().get("level"))));
                savePanelLayout();
                return true;
            }
            case LOG_LINE -> {
                Object rawSequence = hoveredNode.props().get("alertSequence");
                if (rawSequence instanceof Number sequence) {
                    controller.toggleLogExpanded(sequence.longValue());
                    return true;
                }
                return false;
            }
            case LOG_COPY_BUTTON -> {
                String fullText = stringProp(hoveredNode, "fullText");
                if (!fullText.isEmpty()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(fullText);
                    return true;
                }
                return false;
            }
            default -> {
                return super.mouseClicked(mouseX, mouseY, button);
            }
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (latestFrame == null) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        double logicalMouseX = logicalMouseX(mouseX);
        double logicalMouseY = logicalMouseY(mouseY);
        if (resizingPanelId != null && resizingEdge != ResizeEdge.NONE) {
            updatePanelResize(logicalMouseX, logicalMouseY);
            return true;
        }
        if (resizingSlotId != null && resizingSlotEdge != ResizeEdge.NONE) {
            updateSlotResize(logicalMouseX, logicalMouseY);
            return true;
        }
        if (draggingPanelId != null) {
            updatePanelDrag(logicalMouseX, logicalMouseY);
            return true;
        }
        if (draggingWindowId != null) {
            updateWindowDropPreview(mouseX, mouseY, logicalMouseX, logicalMouseY);
            return true;
        }
        if (draggingSliderControlId != null) {
            DashboardPrimitive node = findControlNode(draggingSliderControlId);
            if (node == null) {
                draggingSliderControlId = null;
                return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }
            updateSliderValue(node, logicalMouseX);
            return true;
        }
        if (draggingScrollArea != null) {
            if (draggingScrollHorizontal) {
                HorizontalScrollbarInfo scrollbar = horizontalScrollbarInfo(draggingScrollArea);
                if (scrollbar == null) {
                    draggingScrollArea = null;
                    draggingScrollHorizontal = false;
                    return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
                }
                setHorizontalScrollFromThumb(draggingScrollArea, scrollbar,
                        logicalMouseX - draggingScrollGrabOffset - scrollbar.track().bounds().x());
                return true;
            }
            ScrollbarInfo scrollbar = scrollbarInfo(draggingScrollArea);
            if (scrollbar == null) {
                draggingScrollArea = null;
                return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }
            setScrollFromThumb(draggingScrollArea, scrollbar,
                    logicalMouseY - draggingScrollGrabOffset - scrollbar.track().bounds().y());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double logicalMouseX = logicalMouseX(mouseX);
        double logicalMouseY = logicalMouseY(mouseY);
        draggingSliderControlId = null;
        draggingScrollArea = null;
        draggingScrollHorizontal = false;

        boolean layoutChanged = false;
        if (draggingPanelId != null) {
            draggingPanelId = null;
            draggingPanelBoundsStart = null;
            layoutChanged = true;
        }
        if (resizingPanelId != null) {
            resizingPanelId = null;
            resizingEdge = ResizeEdge.NONE;
            resizingPanelBoundsStart = null;
            layoutChanged = true;
        }
        if (resizingSlotId != null) {
            resizingSlotId = null;
            resizingSlotEdge = ResizeEdge.NONE;
            layoutChanged = true;
        }
        if (draggingWindowId != null) {
            boolean movedWindow = Math.abs(mouseX - draggingWindowStartX) + Math.abs(mouseY - draggingWindowStartY) > dragThresholdPx();
            if (!movedWindow) {
                draggingWindowId = null;
                draggingWindowSourcePanelId = null;
                controller.clearTabDropPreview();
                return super.mouseReleased(mouseX, mouseY, button);
            }
            DashboardPanelId targetPanelId = panelAt(logicalMouseX, logicalMouseY);
            if (targetPanelId != null) {
                controller.moveWindowToPanel(draggingWindowId, targetPanelId, windowInsertIndexAt(targetPanelId, logicalMouseX, logicalMouseY));
                layoutChanged = true;
            } else if (draggingWindowSourcePanelId != null) {
                controller.setPanelMode(draggingWindowSourcePanelId, DashboardPanelMode.FLOATING);
                controller.setActiveWindow(draggingWindowSourcePanelId, draggingWindowId);
                UiRect bounds = controller.panelFloatingBounds(draggingWindowSourcePanelId);
                if (bounds.width() <= 0 || bounds.height() <= 0) {
                    controller.setPanelFloatingBounds(draggingWindowSourcePanelId, new UiRect(
                            (int) Math.round(logicalMouseX) - minPanelWidth(draggingWindowSourcePanelId) / 2,
                            (int) Math.round(logicalMouseY) - minPanelHeight(draggingWindowSourcePanelId) / 2,
                            minPanelWidth(draggingWindowSourcePanelId),
                            minPanelHeight(draggingWindowSourcePanelId)));
                }
                controller.clampFloatingPanelToScreen(draggingWindowSourcePanelId, logicalWidth(), logicalHeight(),
                        minPanelWidth(draggingWindowSourcePanelId), minPanelHeight(draggingWindowSourcePanelId));
                layoutChanged = true;
            }
            draggingWindowId = null;
            draggingWindowSourcePanelId = null;
            draggingWindowStartX = 0.0D;
            draggingWindowStartY = 0.0D;
            controller.clearTabDropPreview();
        }
        if (layoutChanged) {
            savePanelLayout();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        String scrollArea = scrollAreaAt(logicalMouseX(mouseX), logicalMouseY(mouseY));
        if (scrollArea != null) {
            double step = Math.max(18.0D, logicalUnitsForPhysicalPx(22));
            if (scrollArea.endsWith("-x")) {
                setHorizontalScroll(scrollArea, currentScroll(scrollArea) + (-delta * step));
            } else {
                setScroll(scrollArea, currentScroll(scrollArea) + (-delta * step));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editingNumberControlId == null) {
            return super.charTyped(codePoint, modifiers);
        }
        DashboardPrimitive node = findControlNode(editingNumberControlId);
        ControlSpec controlSpec = node != null && node.props().get("controlSpec") instanceof ControlSpec spec ? spec : null;
        NumericSpec numericSpec = controlSpec != null ? controlSpec.numericSpec() : null;
        if (numericSpec == null) {
            return false;
        }
        if (!acceptsNumberCharacter(codePoint, editingNumberDraft, numericSpec)) {
            return false;
        }
        editingNumberDraft += codePoint;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingNumberControlId != null) {
            if (keyCode == 257 || keyCode == 335) {
                commitNumberEdit();
                return true;
            }
            if (keyCode == 256) {
                cancelNumberEdit();
                return true;
            }
            if (keyCode == 259) {
                if (!editingNumberDraft.isEmpty()) {
                    editingNumberDraft = editingNumberDraft.substring(0, editingNumberDraft.length() - 1);
                }
                return true;
            }
            if (keyCode == 261) {
                editingNumberDraft = "";
                return true;
            }
        }
        if (controller.openChoiceControlId() != null && keyCode == 256) {
            controller.closeChoiceDropdown();
            return true;
        }
        if (keyCode == 256 || this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean handleControlClick(DashboardPrimitive node, double mouseX, double mouseY) {
        if (boolProp(node, "expandable", false) && mouseX < controlBounds(node).x()) {
            controller.toggleExpanded(node.id());
            savePanelLayout();
            return true;
        }
        if (!boolProp(node, "enabled", true)) {
            return true;
        }
        ControlSpec controlSpec = node.props().get("controlSpec") instanceof ControlSpec spec ? spec : null;
        if (controlSpec == null) {
            return false;
        }
        switch (controlSpec.kind()) {
            case TOGGLE -> {
                boolean current = Boolean.TRUE.equals(node.props().get("value"));
                writeControl(node, !current);
                return true;
            }
            case SLIDER -> {
                draggingSliderControlId = String.valueOf(node.props().get("controlId"));
                updateSliderValue(node, mouseX);
                return true;
            }
            case NUMBER -> {
                beginNumberEdit(node);
                return true;
            }
            case CHOICE -> {
                ChoiceSpec choiceSpec = controlSpec.choiceSpec();
                if (choiceSpec == null) {
                    return false;
                }
                List<AdaptiveDashboardRenderer.ChoiceHitBox> hitBoxes = renderer.choiceHitBoxes(node, controller);
                if (!hitBoxes.isEmpty()) {
                    for (AdaptiveDashboardRenderer.ChoiceHitBox hitBox : hitBoxes) {
                        if (hitBox.bounds().contains(mouseX, mouseY)) {
                            writeControl(node, hitBox.value());
                            controller.closeChoiceDropdown();
                            return true;
                        }
                    }
                }
                String controlId = String.valueOf(node.props().get("controlId"));
                if (controlId.equals(controller.openChoiceControlId())) {
                    controller.closeChoiceDropdown();
                } else {
                    controller.setOpenChoiceControlId(controlId);
                }
                return true;
            }
        }
        return false;
    }

    private boolean handleTopbarClick(DashboardPrimitive node) {
        String role = stringProp(node, "role");
        if ("topbar-button".equals(role)) {
            controller.toggleTopbarMenu(stringProp(node, "menuId"));
            return true;
        }
        controller.setOpenTopbarMenuId(null);
        return true;
    }

    private boolean handleTopbarMenuItemClick(DashboardPrimitive node) {
        String role = stringProp(node, "role");
        if ("scale-option".equals(role)) {
            controller.setScaleLevel(intProp(node, "scaleLevel", 3));
            controller.setOpenTopbarMenuId(null);
            dashboardScale = computeDashboardScale();
            scaleContext = UiScaleContext.of(dashboardScale, Math.max(1, width), Math.max(1, height));
            savePanelLayout();
            return true;
        }
        if ("window-toggle".equals(role)) {
            DashboardWindowId windowId = DashboardWindowId.byId(stringProp(node, "windowId"));
            if (windowId == null) {
                return false;
            }
            controller.toggleWindowVisible(windowId);
            DashboardPanelId panelId = controller.windowPanelId(windowId);
            DashboardWindowId active = controller.activeWindow(panelId);
            if (active != null) {
                controller.setActiveWindow(panelId, active);
            }
            savePanelLayout();
            return true;
        }
        return false;
    }

    private boolean handlePopupMenuItemClick(DashboardPrimitive node) {
        String role = stringProp(node, "role");
        if (!"choice-option".equals(role)) {
            return true;
        }
        DashboardPrimitive ownerNode = findControlNode(stringProp(node, "controlId"));
        if (ownerNode == null) {
            controller.closeChoiceDropdown();
            return true;
        }
        writeControl(ownerNode, stringProp(node, "optionValue"));
        controller.closeChoiceDropdown();
        return true;
    }

    private boolean handleWindowTabClick(DashboardPrimitive node, int button, double mouseX, double mouseY) {
        if (button != 0 || boolProp(node, "disabled", false)) {
            return false;
        }
        DashboardWindowId windowId = DashboardWindowId.byId(stringProp(node, "windowId"));
        DashboardPanelId panelId = panelIdOf(node);
        if (windowId == null || panelId == null) {
            return false;
        }
        controller.setActiveWindow(panelId, windowId);
        savePanelLayout();
        draggingWindowId = windowId;
        draggingWindowSourcePanelId = panelId;
        draggingWindowStartX = mouseX;
        draggingWindowStartY = mouseY;
        return true;
    }

    private boolean isTopbarPrimitive(DashboardPrimitive node) {
        if (node == null) {
            return false;
        }
        return node.type() == UiNodeType.TOPBAR
                || node.type() == UiNodeType.TOPBAR_MENU_ITEM
                || (node.type() == UiNodeType.POPUP_PANEL && "topbar-popup-panel".equals(stringProp(node, "role")));
    }

    private boolean isChoicePopupPrimitive(DashboardPrimitive node) {
        if (node == null) {
            return false;
        }
        String role = stringProp(node, "role");
        return (node.type() == UiNodeType.POPUP_PANEL && "choice-dropdown-panel".equals(role))
                || (node.type() == UiNodeType.POPUP_MENU_ITEM && "choice-option".equals(role));
    }

    private boolean isChoiceOwnerPrimitive(DashboardPrimitive node) {
        if (node == null || node.type() != UiNodeType.TREE_CONTROL || controller.openChoiceControlId() == null) {
            return false;
        }
        return controller.openChoiceControlId().equals(stringProp(node, "controlId"));
    }

    private void updateWindowDropPreview(double physicalMouseX, double physicalMouseY, double logicalMouseX, double logicalMouseY) {
        boolean movedWindow = Math.abs(physicalMouseX - draggingWindowStartX) + Math.abs(physicalMouseY - draggingWindowStartY) > dragThresholdPx();
        if (!movedWindow) {
            controller.clearTabDropPreview();
            return;
        }
        controller.setTabDropPreviewPanelId(panelAt(logicalMouseX, logicalMouseY));
    }

    private boolean handlePanelClick(DashboardPrimitive node, double mouseX, double mouseY, int button) {
        String role = stringProp(node, "role");
        DashboardPanelId panelId = panelIdOf(node);
        if ("panel-mode-toggle".equals(role)) {
            return togglePanelMode(panelId);
        }
        if ("panel-resize-handle".equals(role)) {
            return beginPanelResize(node, mouseX, mouseY, button);
        }
        if ("slot-resize-handle".equals(role)) {
            return beginSlotResize(node, button);
        }
        if ("panel-home-slot".equals(role) || "panel-slot-empty".equals(role)) {
            return dockFocusedFloatingPanel(DashboardDockSlotId.ofNullable(stringProp(node, "slotId")));
        }

        String scrollArea = stringProp(node, "scrollArea");
        if (scrollArea.isEmpty()) {
            return false;
        }
        if ("scrollbar-thumb".equals(role)) {
            draggingScrollArea = scrollArea;
            draggingScrollGrabOffset = (int) Math.round(mouseY - node.bounds().y());
            draggingScrollHorizontal = false;
            return true;
        }
        if ("scrollbar-track".equals(role)) {
            ScrollbarInfo scrollbar = scrollbarInfo(scrollArea);
            if (scrollbar == null) {
                return false;
            }
            int thumbHeight = scrollbar.thumb() != null ? scrollbar.thumb().bounds().height() : intProp(node, "thumbHeight", 0);
            setScrollFromThumb(scrollArea, scrollbar, mouseY - scrollbar.track().bounds().y() - thumbHeight / 2.0D);
            return true;
        }
        if ("scrollbar-thumb-x".equals(role)) {
            draggingScrollArea = scrollArea;
            draggingScrollGrabOffset = (int) Math.round(mouseX - node.bounds().x());
            draggingScrollHorizontal = true;
            return true;
        }
        if ("scrollbar-track-x".equals(role)) {
            HorizontalScrollbarInfo scrollbar = horizontalScrollbarInfo(scrollArea);
            if (scrollbar == null) {
                return false;
            }
            int thumbWidth = scrollbar.thumb() != null ? scrollbar.thumb().bounds().width() : intProp(node, "thumbWidth", 0);
            setHorizontalScrollFromThumb(scrollArea, scrollbar, mouseX - scrollbar.track().bounds().x() - thumbWidth / 2.0D);
            return true;
        }
        return false;
    }

    private boolean togglePanelMode(DashboardPanelId panelId) {
        if (panelId == null) {
            return false;
        }
        if (controller.isFloating(panelId)) {
            if (controller.dockPreviewPanelId() == panelId) {
                controller.clearDockPreview();
            } else {
                controller.setDockPreview(panelId, null);
            }
        } else {
            controller.clearDockPreview();
            controller.setPanelMode(panelId, DashboardPanelMode.FLOATING);
            UiRect bounds = controller.panelFloatingBounds(panelId);
            if (bounds.width() <= 0 || bounds.height() <= 0) {
                controller.setPanelFloatingBounds(panelId, currentSlotBounds(panelId));
            }
            controller.clampFloatingPanelToScreen(panelId, logicalWidth(), logicalHeight(), minPanelWidth(panelId), minPanelHeight(panelId));
            controller.focusPanel(panelId);
        }
        savePanelLayout();
        return true;
    }

    private boolean beginPanelDrag(DashboardPrimitive node, double mouseX, double mouseY, int button) {
        DashboardPanelId panelId = panelIdOf(node);
        if (button != 0 || panelId == null || !controller.isFloating(panelId)) {
            return false;
        }
        draggingPanelId = panelId;
        draggingPanelBoundsStart = controller.panelFloatingBounds(panelId);
        if (draggingPanelBoundsStart.width() <= 0 || draggingPanelBoundsStart.height() <= 0) {
            draggingPanelBoundsStart = currentSlotBounds(panelId);
        }
        draggingPanelGrabOffsetX = (int) Math.round(mouseX - draggingPanelBoundsStart.x());
        draggingPanelGrabOffsetY = (int) Math.round(mouseY - draggingPanelBoundsStart.y());
        controller.focusPanel(panelId);
        if (controller.dockPreviewPanelId() == panelId) {
            controller.clearDockPreview();
        }
        return true;
    }

    private void updatePanelDrag(double mouseX, double mouseY) {
        if (draggingPanelId == null || draggingPanelBoundsStart == null) {
            return;
        }
        UiRect updated = new UiRect(
                (int) Math.round(mouseX) - draggingPanelGrabOffsetX,
                (int) Math.round(mouseY) - draggingPanelGrabOffsetY,
                draggingPanelBoundsStart.width(),
                draggingPanelBoundsStart.height());
        controller.setPanelFloatingBounds(draggingPanelId, updated);
        controller.clampFloatingPanelToScreen(draggingPanelId, logicalWidth(), logicalHeight(), minPanelWidth(draggingPanelId), minPanelHeight(draggingPanelId));
    }

    private boolean beginPanelResize(DashboardPrimitive node, double mouseX, double mouseY, int button) {
        DashboardPanelId panelId = panelIdOf(node);
        if (button != 0 || panelId == null || !controller.isFloating(panelId)) {
            return false;
        }
        ResizeEdge edge = ResizeEdge.byId(stringProp(node, "edge"));
        if (edge == ResizeEdge.NONE) {
            return false;
        }
        resizingPanelId = panelId;
        resizingEdge = edge;
        resizingPanelBoundsStart = controller.panelFloatingBounds(panelId);
        if (resizingPanelBoundsStart.width() <= 0 || resizingPanelBoundsStart.height() <= 0) {
            resizingPanelBoundsStart = currentSlotBounds(panelId);
        }
        controller.focusPanel(panelId);
        return true;
    }

    private boolean beginSlotResize(DashboardPrimitive node, int button) {
        if (button != 0) {
            return false;
        }
        DashboardDockSlotId slotId = DashboardDockSlotId.ofNullable(stringProp(node, "slotId"));
        ResizeEdge edge = ResizeEdge.byId(stringProp(node, "edge"));
        if (slotId == null || edge == ResizeEdge.NONE) {
            return false;
        }
        resizingSlotId = slotId;
        resizingSlotEdge = edge;
        return true;
    }

    private void updatePanelResize(double mouseX, double mouseY) {
        if (resizingPanelId == null || resizingPanelBoundsStart == null || resizingEdge == ResizeEdge.NONE) {
            return;
        }
        int minWidth = minPanelWidth(resizingPanelId);
        int minHeight = minPanelHeight(resizingPanelId);
        int left = resizingPanelBoundsStart.x();
        int top = resizingPanelBoundsStart.y();
        int right = resizingPanelBoundsStart.right();
        int bottom = resizingPanelBoundsStart.bottom();
        int mouseXi = (int) Math.round(mouseX);
        int mouseYi = (int) Math.round(mouseY);

        if (resizingEdge.west) {
            left = Mth.clamp(mouseXi, 0, right - minWidth);
        }
        if (resizingEdge.east) {
            right = Mth.clamp(mouseXi, left + minWidth, logicalWidth());
        }
        if (resizingEdge.north) {
            top = Mth.clamp(mouseYi, 0, bottom - minHeight);
        }
        if (resizingEdge.south) {
            bottom = Mth.clamp(mouseYi, top + minHeight, logicalHeight());
        }

        controller.setPanelFloatingBounds(resizingPanelId, new UiRect(left, top, right - left, bottom - top));
        controller.clampFloatingPanelToScreen(resizingPanelId, logicalWidth(), logicalHeight(), minWidth, minHeight);
    }

    private void updateSlotResize(double mouseX, double mouseY) {
        if (resizingSlotId == null || resizingSlotEdge == ResizeEdge.NONE) {
            return;
        }
        DashboardWorkspaceLayout layout = currentWorkspaceLayout();
        double ratio = controller.slotSizeRatio(resizingSlotId, defaultSlotRatio(resizingSlotId));
        UiRect shell = layout.shellBounds();
        if (resizingSlotEdge.east) {
            ratio = (mouseX - shell.x()) / Math.max(1.0D, shell.width());
        } else if (resizingSlotEdge.west) {
            ratio = (shell.right() - mouseX) / Math.max(1.0D, shell.width());
        } else if (resizingSlotEdge.north) {
            ratio = (shell.bottom() - mouseY) / Math.max(1.0D, shell.height());
        } else if (resizingSlotEdge.south) {
            ratio = (mouseY - shell.y()) / Math.max(1.0D, shell.height());
        }
        DashboardPrimitive handle = findPrimitiveById("slot-resize/" + resizingSlotId.value() + "/" + stringPropForEdge(resizingSlotEdge));
        double minRatio = handle != null && handle.props().get("minRatio") instanceof Number min ? min.doubleValue() : 0.10D;
        double maxRatio = handle != null && handle.props().get("maxRatio") instanceof Number max ? max.doubleValue() : 0.90D;
        controller.setSlotSizeRatio(resizingSlotId, Mth.clamp(ratio, minRatio, maxRatio));
    }

    private void updateSliderValue(DashboardPrimitive node, double mouseX) {
        ControlSpec controlSpec = node.props().get("controlSpec") instanceof ControlSpec spec ? spec : null;
        if (controlSpec == null || controlSpec.numericSpec() == null) {
            return;
        }
        NumericSpec numericSpec = controlSpec.numericSpec();
        UiRect control = controlBounds(node);
        UiRect track = DashboardControlLayout.sliderTrackBounds(control, 1.0f);
        double normalized = (mouseX - track.x()) / Math.max(1.0D, track.width());
        normalized = Mth.clamp(normalized, 0.0D, 1.0D);
        double value = numericSpec.minValue() + (numericSpec.maxValue() - numericSpec.minValue()) * normalized;
        double snapped = snapNumericValue(value, numericSpec);
        if (numericSpec.numericKind() == NumericKind.INTEGER) {
            writeControl(node, (int) Math.round(snapped));
        } else {
            writeControl(node, (float) snapped);
        }
    }

    private void writeControl(DashboardPrimitive node, Object value) {
        Object controlId = node.props().get("controlId");
        if (controlId == null || dataSource == null) {
            return;
        }
        dataSource.controlAccessor().writeValue(String.valueOf(controlId), value, dataSource.runtimeHost().settingRegistry(), null);
    }

    private void beginNumberEdit(DashboardPrimitive node) {
        editingNumberControlId = String.valueOf(node.props().get("controlId"));
        editingNumberOriginalValue = node.props().get("value");
        ControlSpec controlSpec = node.props().get("controlSpec") instanceof ControlSpec spec ? spec : null;
        NumericSpec numericSpec = controlSpec != null ? controlSpec.numericSpec() : null;
        editingNumberDraft = numericSpec != null
                ? formatNumericForEditing(numericSpec, editingNumberOriginalValue)
                : String.valueOf(editingNumberOriginalValue);
    }

    private void cancelNumberEdit() {
        editingNumberControlId = null;
        editingNumberDraft = "";
        editingNumberOriginalValue = null;
    }

    private void commitNumberEdit() {
        if (editingNumberControlId == null) {
            return;
        }
        DashboardPrimitive node = findControlNode(editingNumberControlId);
        if (node != null) {
            ControlSpec controlSpec = node.props().get("controlSpec") instanceof ControlSpec spec ? spec : null;
            NumericSpec numericSpec = controlSpec != null ? controlSpec.numericSpec() : null;
            Object parsed = coerceNumberDraft(editingNumberDraft, numericSpec, editingNumberOriginalValue);
            writeControl(node, parsed);
        }
        cancelNumberEdit();
    }

    private Object coerceNumberDraft(String draft, NumericSpec numericSpec, Object fallback) {
        if (numericSpec == null) {
            return fallback;
        }
        String normalized = draft != null ? draft.trim() : "";
        if (normalized.isEmpty() || "-".equals(normalized) || ".".equals(normalized) || "-.".equals(normalized)) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(normalized);
            double snapped = snapNumericValue(parsed, numericSpec);
            return numericSpec.numericKind() == NumericKind.INTEGER ? (int) Math.round(snapped) : (float) snapped;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double snapNumericValue(double value, NumericSpec numericSpec) {
        double clamped = Mth.clamp(value, numericSpec.minValue(), numericSpec.maxValue());
        double step = Math.max(0.000001D, numericSpec.step());
        double snapped = numericSpec.minValue() + Math.round((clamped - numericSpec.minValue()) / step) * step;
        return Mth.clamp(snapped, numericSpec.minValue(), numericSpec.maxValue());
    }

    private String formatNumericForEditing(NumericSpec numericSpec, Object value) {
        if (!(value instanceof Number number)) {
            return "";
        }
        return numericSpec.numericKind() == NumericKind.INTEGER
                ? Integer.toString((int) Math.round(number.doubleValue()))
                : Double.toString(number.doubleValue());
    }

    private boolean acceptsNumberCharacter(char codePoint, String currentDraft, NumericSpec numericSpec) {
        if (Character.isDigit(codePoint)) {
            return true;
        }
        if (codePoint == '-' && currentDraft.isEmpty()) {
            return true;
        }
        return codePoint == '.' && numericSpec.numericKind() == NumericKind.FLOAT && !currentDraft.contains(".");
    }

    private void acknowledgeVisibleAlerts() {
        if (latestSnapshot == null) {
            return;
        }
        controller.acknowledgeAlertsUpTo(latestSnapshot.latestAlertSequence());
        sessionAcknowledgedAlertSequence = Math.max(sessionAcknowledgedAlertSequence, controller.acknowledgedAlertSequence());
    }

    private void loadPanelLayout() {
        if (panelLayoutLoaded) {
            return;
        }
        String workspaceId = workspaceProfile.workspaceId();
        controller.setScaleLevel(Config.getDashboardScaleLevel(workspaceId));
        for (DashboardPanelId panelId : DashboardPanelId.values()) {
            DashboardDockSlotId defaultHomeSlot = workspaceProfile.defaultHomeSlot(panelId);
            controller.setPanelHomeSlotId(panelId, Config.getDashboardPanelHomeSlotId(workspaceId, panelId, defaultHomeSlot));
            controller.setPanelDockedSlotId(panelId, Config.getDashboardPanelDockedSlotId(workspaceId, panelId, controller.panelHomeSlotId(panelId)));
            controller.setPanelMode(panelId, Config.getDashboardPanelMode(workspaceId, panelId));
            controller.setPanelFloatingBounds(panelId, Config.getDashboardPanelFloatingBounds(workspaceId, panelId));
        }
        for (DashboardWindowId windowId : DashboardWindowId.values()) {
            controller.setWindowVisible(windowId, Config.getDashboardWindowVisible(workspaceId, windowId));
            controller.setWindowPanelId(windowId, Config.getDashboardWindowPanelId(workspaceId, windowId));
        }
        for (DashboardPanelId panelId : DashboardPanelId.values()) {
            List<DashboardWindowId> current = controller.windowsForPanel(panelId);
            List<DashboardWindowId> saved = Config.getDashboardPanelWindowOrder(workspaceId, panelId, current);
            List<DashboardWindowId> ordered = new ArrayList<>();
            for (DashboardWindowId windowId : saved) {
                if (controller.windowPanelId(windowId) == panelId && !ordered.contains(windowId)) {
                    ordered.add(windowId);
                }
            }
            for (DashboardWindowId windowId : current) {
                if (controller.windowPanelId(windowId) == panelId && !ordered.contains(windowId)) {
                    ordered.add(windowId);
                }
            }
            controller.setPanelWindows(panelId, ordered);
            DashboardWindowId active = Config.getDashboardActiveWindow(workspaceId, panelId, controller.activeWindow(panelId));
            if (active != null && controller.windowPanelId(active) == panelId && controller.isWindowVisible(active)) {
                controller.setActiveWindow(panelId, active);
            }
        }
        for (DashboardDockSlotSpec slotSpec : workspaceProfile.slots()) {
            controller.setSlotSizeRatio(slotSpec.slotId(),
                    Config.getDashboardSlotSizeRatio(workspaceId, slotSpec.slotId(), defaultSlotRatio(slotSpec.slotId())));
        }
        controller.setMemorySectionExpanded(Config.getDashboardMemorySectionExpanded(workspaceId));
        controller.setMacroConstantsExpanded(Config.getDashboardMacroConstantsExpanded(workspaceId));
        controller.setDiagnosticFilters(Config.getDashboardDiagnosticFilters(workspaceId));
        controller.setSettingsScroll(Config.getDashboardPanelVerticalScroll(workspaceId, DashboardPanelId.SETTINGS));
        controller.setMetricsScroll(Config.getDashboardPanelVerticalScroll(workspaceId, DashboardPanelId.METRICS));
        controller.setDiagnosticsScroll(Config.getDashboardPanelVerticalScroll(workspaceId, DashboardPanelId.DIAGNOSTICS));
        controller.setSettingsHorizontalScroll(Config.getDashboardPanelHorizontalScroll(workspaceId, DashboardPanelId.SETTINGS));
        controller.setMetricsHorizontalScroll(Config.getDashboardPanelHorizontalScroll(workspaceId, DashboardPanelId.METRICS));
        controller.setDiagnosticsHorizontalScroll(Config.getDashboardPanelHorizontalScroll(workspaceId, DashboardPanelId.DIAGNOSTICS));
        controller.clearDockPreview();
        panelLayoutLoaded = true;
    }

    private void savePanelLayout() {
        if (!panelLayoutLoaded) {
            return;
        }
        String workspaceId = workspaceProfile.workspaceId();
        Config.setDashboardScaleLevel(workspaceId, controller.scaleLevel());
        for (DashboardPanelId panelId : DashboardPanelId.values()) {
            Config.setDashboardPanelHomeSlotId(workspaceId, panelId, controller.panelHomeSlotId(panelId));
            Config.setDashboardPanelDockedSlotId(workspaceId, panelId, controller.panelDockedSlotId(panelId));
            Config.setDashboardPanelMode(workspaceId, panelId, controller.panelMode(panelId));
            Config.setDashboardPanelFloatingBounds(workspaceId, panelId, controller.panelFloatingBounds(panelId));
            Config.setDashboardPanelWindowOrder(workspaceId, panelId, controller.windowsForPanel(panelId));
            DashboardWindowId activeWindow = controller.activeWindow(panelId);
            if (activeWindow != null) {
                Config.setDashboardActiveWindow(workspaceId, panelId, activeWindow);
            }
        }
        for (DashboardWindowId windowId : DashboardWindowId.values()) {
            Config.setDashboardWindowVisible(workspaceId, windowId, controller.isWindowVisible(windowId));
            Config.setDashboardWindowPanelId(workspaceId, windowId, controller.windowPanelId(windowId));
        }
        for (DashboardDockSlotSpec slotSpec : workspaceProfile.slots()) {
            Config.setDashboardSlotSizeRatio(workspaceId, slotSpec.slotId(),
                    controller.slotSizeRatio(slotSpec.slotId(), defaultSlotRatio(slotSpec.slotId())));
        }
        Config.setDashboardMemorySectionExpanded(workspaceId, controller.memorySectionExpanded());
        Config.setDashboardMacroConstantsExpanded(workspaceId, controller.macroConstantsExpanded());
        Config.setDashboardDiagnosticFilters(workspaceId, controller.diagnosticFilters());
        Config.setDashboardPanelVerticalScroll(workspaceId, DashboardPanelId.SETTINGS, controller.settingsScroll());
        Config.setDashboardPanelVerticalScroll(workspaceId, DashboardPanelId.METRICS, controller.metricsScroll());
        Config.setDashboardPanelVerticalScroll(workspaceId, DashboardPanelId.DIAGNOSTICS, controller.diagnosticsScroll());
        Config.setDashboardPanelHorizontalScroll(workspaceId, DashboardPanelId.SETTINGS, controller.settingsHorizontalScroll());
        Config.setDashboardPanelHorizontalScroll(workspaceId, DashboardPanelId.METRICS, controller.metricsHorizontalScroll());
        Config.setDashboardPanelHorizontalScroll(workspaceId, DashboardPanelId.DIAGNOSTICS, controller.diagnosticsHorizontalScroll());
        if (expandedDefaults) {
            Config.setDashboardExpandedTreeNodes(workspaceId, persistedExpandedTreeNodeIds());
        }
    }

    private void ensurePanelLayoutState() {
        for (DashboardPanelId panelId : DashboardPanelId.values()) {
            DashboardDockSlotId defaultHomeSlot = workspaceProfile.defaultHomeSlot(panelId);
            if (controller.panelHomeSlotId(panelId) == null) {
                controller.setPanelHomeSlotId(panelId, defaultHomeSlot);
            }
            if (controller.panelDockedSlotId(panelId) == null) {
                controller.setPanelDockedSlotId(panelId, controller.panelHomeSlotId(panelId));
            }
            if (!controller.isFloating(panelId)) {
                continue;
            }
            UiRect bounds = controller.panelFloatingBounds(panelId);
            if (bounds.width() <= 0 || bounds.height() <= 0) {
                controller.setPanelFloatingBounds(panelId, currentSlotBounds(panelId));
            }
            controller.clampFloatingPanelToScreen(panelId, logicalWidth(), logicalHeight(), minPanelWidth(panelId), minPanelHeight(panelId));
        }
    }

    private List<DashboardPrimitive> dashboardPrimitives() {
        if (latestFrame == null) {
            return List.of();
        }
        List<DashboardPrimitive> primitives = new ArrayList<>();
        for (UiPrimitive primitive : latestFrame.primitives()) {
            if (primitive instanceof DashboardPrimitive dashboardPrimitive) {
                primitives.add(dashboardPrimitive);
            }
        }
        return primitives;
    }

    private DashboardPrimitive findControlNode(String controlId) {
        if (latestFrame == null) {
            return null;
        }
        for (DashboardPrimitive node : dashboardPrimitives()) {
            if (node.type() == UiNodeType.TREE_CONTROL && controlId.equals(String.valueOf(node.props().get("controlId")))) {
                return node;
            }
        }
        return null;
    }

    private UiRect controlBounds(DashboardPrimitive node) {
        ControlSpec controlSpec = node.props().get("controlSpec") instanceof ControlSpec spec ? spec : null;
        return DashboardControlLayout.controlBounds(node.bounds(), controlSpec, 1.0f);
    }

    private DashboardPrimitive findPrimitiveById(String id) {
        if (latestFrame == null) {
            return null;
        }
        for (DashboardPrimitive node : dashboardPrimitives()) {
            if (id.equals(node.id())) {
                return node;
            }
        }
        return null;
    }

    private DashboardPanelId panelIdOf(DashboardPrimitive node) {
        Object value = node != null ? node.props().get("panelId") : null;
        return DashboardPanelId.byId(value != null ? String.valueOf(value) : null);
    }

    private UiRect currentSlotBounds(DashboardPanelId panelId) {
        DashboardDockSlotId slotId = controller.panelDockedSlotId(panelId);
        if (slotId == null) {
            slotId = controller.panelHomeSlotId(panelId);
        }
        if (slotId == null) {
            slotId = workspaceProfile.defaultHomeSlot(panelId);
        }
        DashboardPrimitive primitive = slotId != null ? findPrimitiveById("panel-home-slot/" + slotId.value()) : null;
        return primitive != null ? primitive.bounds() : defaultSlotBounds(slotId);
    }

    private UiRect defaultSlotBounds(DashboardDockSlotId slotId) {
        DashboardWorkspaceLayout layout = currentWorkspaceLayout();
        UiRect slotBounds = layout.slotBounds(slotId);
        return slotBounds != null ? slotBounds : new UiRect(0, 0, logicalWidth(), logicalHeight());
    }

    private DashboardWorkspaceLayout currentWorkspaceLayout() {
        int topbarHeight = dashboardTopbarHeight();
        DashboardWorkspaceLayout raw = workspaceProfile.layout(logicalWidth(), Math.max(1, logicalHeight() - topbarHeight), 1.0f, controller);
        Map<DashboardDockSlotId, UiRect> shiftedSlots = new LinkedHashMap<>();
        for (Map.Entry<DashboardDockSlotId, UiRect> entry : raw.slotBounds().entrySet()) {
            shiftedSlots.put(entry.getKey(), shift(entry.getValue(), 0, topbarHeight));
        }
        List<DashboardDockResizeHandle> shiftedHandles = new ArrayList<>();
        for (DashboardDockResizeHandle handle : raw.resizeHandles()) {
            shiftedHandles.add(new DashboardDockResizeHandle(handle.id(), handle.slotId(), handle.edge(),
                    shift(handle.bounds(), 0, topbarHeight), handle.minRatio(), handle.maxRatio()));
        }
        return new DashboardWorkspaceLayout(raw.workspaceId(), shift(raw.shellBounds(), 0, topbarHeight),
                raw.slotSpecs(), shiftedSlots, shiftedHandles);
    }

    private UiRect shift(UiRect rect, int dx, int dy) {
        return new UiRect(rect.x() + dx, rect.y() + dy, rect.width(), rect.height());
    }

    private int dashboardTopbarHeight() {
        return 24;
    }

    private DashboardDockSlotId slotAt(double mouseX, double mouseY) {
        DashboardWorkspaceLayout layout = currentWorkspaceLayout();
        for (var entry : layout.slotBounds().entrySet()) {
            if (entry.getValue().contains(mouseX, mouseY)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private DashboardPanelId panelAt(double mouseX, double mouseY) {
        if (latestFrame != null) {
            for (DashboardPrimitive node : dashboardPrimitives()) {
                if ("panel".equals(stringProp(node, "role")) && node.bounds().contains(mouseX, mouseY)) {
                    DashboardPanelId panelId = panelIdOf(node);
                    if (panelId != null) {
                        return panelId;
                    }
                }
            }
        }
        DashboardDockSlotId slotId = slotAt(mouseX, mouseY);
        if (slotId == null) {
            return null;
        }
        for (DashboardPanelId panelId : DashboardPanelId.values()) {
            if (slotId.equals(controller.panelDockedSlotId(panelId)) || slotId.equals(controller.panelHomeSlotId(panelId))) {
                return panelId;
            }
        }
        return null;
    }

    private int windowInsertIndexAt(DashboardPanelId targetPanelId, double mouseX, double mouseY) {
        List<DashboardWindowId> windows = controller.windowsForPanel(targetPanelId);
        int fallback = windows.size();
        int index = 0;
        for (DashboardPrimitive node : dashboardPrimitives()) {
            if (node.type() != UiNodeType.WINDOW_TAB || panelIdOf(node) != targetPanelId || boolProp(node, "disabled", false)) {
                continue;
            }
            if (node.bounds().contains(mouseX, mouseY)) {
                return mouseX < node.bounds().x() + node.bounds().width() / 2.0D ? index : index + 1;
            }
            index++;
        }
        return fallback;
    }

    private double defaultSlotRatio(DashboardDockSlotId slotId) {
        DashboardController defaults = new DashboardController();
        DashboardWorkspaceLayout layout = workspaceProfile.layout(logicalWidth(), logicalHeight(), 1.0f, defaults);
        UiRect slotBounds = layout.slotBounds(slotId);
        if (slotBounds == null) {
            return 0.0D;
        }
        UiRect shell = layout.shellBounds();
        if (shell.width() <= 0 || shell.height() <= 0) {
            return 0.0D;
        }
        return switch (stringPropForRole(slotId)) {
            case "BOTTOM_OUTPUT" -> slotBounds.height() / (double) shell.height();
            default -> slotBounds.width() / (double) shell.width();
        };
    }

    private String stringPropForRole(DashboardDockSlotId slotId) {
        for (DashboardDockSlotSpec slotSpec : workspaceProfile.slots()) {
            if (slotSpec.slotId().equals(slotId)) {
                return slotSpec.role().name();
            }
        }
        return "CUSTOM";
    }

    private boolean dockFocusedFloatingPanel(DashboardDockSlotId slotId) {
        DashboardPanelId floatingPanelId = controller.dockPreviewPanelId();
        if (slotId == null || floatingPanelId == null) {
            return false;
        }
        controller.setPanelDockedSlotId(floatingPanelId, slotId);
        controller.setPanelMode(floatingPanelId, DashboardPanelMode.DOCKED);
        controller.clearDockPreview();
        savePanelLayout();
        return true;
    }

    private boolean shouldCancelDockSelection(DashboardPrimitive hoveredNode) {
        String role = stringProp(hoveredNode, "role");
        if ("panel-home-slot".equals(role) || "panel-slot-empty".equals(role)) {
            return false;
        }
        return !"panel-mode-toggle".equals(role) || panelIdOf(hoveredNode) != controller.dockPreviewPanelId();
    }

    private String stringPropForEdge(ResizeEdge edge) {
        return switch (edge) {
            case N -> "N";
            case S -> "S";
            case W -> "W";
            case E -> "E";
            case NW -> "NW";
            case NE -> "NE";
            case SW -> "SW";
            case SE -> "SE";
            case NONE -> "";
        };
    }

    private String scrollAreaAt(double mouseX, double mouseY) {
        if (latestFrame == null) {
            return null;
        }
        List<DashboardPrimitive> nodes = dashboardPrimitives();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            DashboardPrimitive node = nodes.get(i);
            String role = stringProp(node, "role");
            if (node.type() == UiNodeType.PANEL && ("scroll-region".equals(role) || "scroll-region-x".equals(role))
                    && node.bounds().contains(mouseX, mouseY)) {
                return stringProp(node, "scrollArea");
            }
        }
        return null;
    }

    private boolean clampScrolls() {
        boolean changed = false;
        changed |= setScroll("settings", controller.settingsScroll());
        changed |= setScroll("metrics", controller.metricsScroll());
        changed |= setScroll("diagnostics", controller.diagnosticsScroll());
        changed |= setHorizontalScroll("settings-x", controller.settingsHorizontalScroll());
        changed |= setHorizontalScroll("metrics-x", controller.metricsHorizontalScroll());
        changed |= setHorizontalScroll("diagnostics-x", controller.diagnosticsHorizontalScroll());
        return changed;
    }

    private boolean setScroll(String area, double requested) {
        ScrollRegionInfo region = scrollRegionInfo(area);
        double max = region == null ? 0.0D : Math.max(0.0D, region.contentHeight() - region.viewportHeight());
        double clamped = Mth.clamp(requested, 0.0D, max);
        double previous = currentScroll(area);
        assignScroll(area, clamped);
        return Math.abs(previous - clamped) > 0.01D;
    }

    private boolean setHorizontalScroll(String area, double requested) {
        HorizontalScrollRegionInfo region = horizontalScrollRegionInfo(area);
        double max = region == null ? 0.0D : Math.max(0.0D, region.contentWidth() - region.viewportWidth());
        double clamped = Mth.clamp(requested, 0.0D, max);
        double previous = currentScroll(area);
        assignScroll(area, clamped);
        return Math.abs(previous - clamped) > 0.01D;
    }

    private double currentScroll(String area) {
        return switch (area) {
            case "settings" -> controller.settingsScroll();
            case "metrics" -> controller.metricsScroll();
            case "diagnostics" -> controller.diagnosticsScroll();
            case "settings-x" -> controller.settingsHorizontalScroll();
            case "metrics-x" -> controller.metricsHorizontalScroll();
            case "diagnostics-x" -> controller.diagnosticsHorizontalScroll();
            default -> 0.0D;
        };
    }

    private void assignScroll(String area, double value) {
        switch (area) {
            case "settings" -> controller.setSettingsScroll(value);
            case "metrics" -> controller.setMetricsScroll(value);
            case "diagnostics" -> controller.setDiagnosticsScroll(value);
            case "settings-x" -> controller.setSettingsHorizontalScroll(value);
            case "metrics-x" -> controller.setMetricsHorizontalScroll(value);
            case "diagnostics-x" -> controller.setDiagnosticsHorizontalScroll(value);
            default -> {
            }
        }
    }

    private ScrollRegionInfo scrollRegionInfo(String area) {
        if (latestFrame == null) {
            return null;
        }
        for (DashboardPrimitive node : dashboardPrimitives()) {
            if (node.type() == UiNodeType.PANEL
                    && "scroll-region".equals(stringProp(node, "role"))
                    && area.equals(stringProp(node, "scrollArea"))) {
                return new ScrollRegionInfo(node.bounds(),
                        intProp(node, "viewportHeight", node.bounds().height()),
                        intProp(node, "contentHeight", node.bounds().height()));
            }
        }
        return null;
    }

    private HorizontalScrollRegionInfo horizontalScrollRegionInfo(String area) {
        if (latestFrame == null) {
            return null;
        }
        for (DashboardPrimitive node : dashboardPrimitives()) {
            if (node.type() == UiNodeType.PANEL
                    && "scroll-region-x".equals(stringProp(node, "role"))
                    && area.equals(stringProp(node, "scrollArea"))) {
                return new HorizontalScrollRegionInfo(node.bounds(),
                        intProp(node, "viewportWidth", node.bounds().width()),
                        intProp(node, "contentWidth", node.bounds().width()));
            }
        }
        return null;
    }

    private ScrollbarInfo scrollbarInfo(String area) {
        if (latestFrame == null) {
            return null;
        }
        DashboardPrimitive track = null;
        DashboardPrimitive thumb = null;
        for (DashboardPrimitive node : dashboardPrimitives()) {
            if (node.type() != UiNodeType.PANEL || !area.equals(stringProp(node, "scrollArea"))) {
                continue;
            }
            String role = stringProp(node, "role");
            if ("scrollbar-track".equals(role)) {
                track = node;
            } else if ("scrollbar-thumb".equals(role)) {
                thumb = node;
            }
        }
        if (track == null) {
            return null;
        }
        return new ScrollbarInfo(track, thumb,
                intProp(track, "viewportHeight", track.bounds().height()),
                intProp(track, "contentHeight", track.bounds().height()));
    }

    private HorizontalScrollbarInfo horizontalScrollbarInfo(String area) {
        if (latestFrame == null) {
            return null;
        }
        DashboardPrimitive track = null;
        DashboardPrimitive thumb = null;
        for (DashboardPrimitive node : dashboardPrimitives()) {
            if (node.type() != UiNodeType.PANEL || !area.equals(stringProp(node, "scrollArea"))) {
                continue;
            }
            String role = stringProp(node, "role");
            if ("scrollbar-track-x".equals(role)) {
                track = node;
            } else if ("scrollbar-thumb-x".equals(role)) {
                thumb = node;
            }
        }
        if (track == null) {
            return null;
        }
        return new HorizontalScrollbarInfo(track, thumb,
                intProp(track, "viewportWidth", track.bounds().width()),
                intProp(track, "contentWidth", track.bounds().width()));
    }

    private void setScrollFromThumb(String area, ScrollbarInfo scrollbar, double thumbOffset) {
        int thumbHeight = scrollbar.thumb() != null ? scrollbar.thumb().bounds().height() : intProp(scrollbar.track(), "thumbHeight", 0);
        int travel = Math.max(1, scrollbar.track().bounds().height() - Math.max(1, thumbHeight));
        double relative = Mth.clamp(thumbOffset, 0.0D, travel);
        double maxScroll = Math.max(0.0D, scrollbar.contentHeight() - scrollbar.viewportHeight());
        double scroll = maxScroll <= 0.0D ? 0.0D : (relative / travel) * maxScroll;
        setScroll(area, scroll);
    }

    private void setHorizontalScrollFromThumb(String area, HorizontalScrollbarInfo scrollbar, double thumbOffset) {
        int thumbWidth = scrollbar.thumb() != null ? scrollbar.thumb().bounds().width() : intProp(scrollbar.track(), "thumbWidth", 0);
        int travel = Math.max(1, scrollbar.track().bounds().width() - Math.max(1, thumbWidth));
        double relative = Mth.clamp(thumbOffset, 0.0D, travel);
        double maxScroll = Math.max(0.0D, scrollbar.contentWidth() - scrollbar.viewportWidth());
        double scroll = maxScroll <= 0.0D ? 0.0D : (relative / travel) * maxScroll;
        setHorizontalScroll(area, scroll);
    }

    private void restoreTreeExpansion(DashboardViewSnapshot snapshot) {
        String workspaceId = workspaceProfile.workspaceId();
        if (!Config.hasDashboardExpandedTreeNodes(workspaceId)) {
            controller.setExpandedTreeNodes(List.of());
            expandDefaults(snapshot.settingRoots());
            expandDefaults(snapshot.macroRoots());
            return;
        }
        Set<String> validNodeIds = expandableTreeNodeIds(snapshot);
        List<String> restored = new ArrayList<>();
        for (String nodeId : Config.getDashboardExpandedTreeNodes(workspaceId)) {
            if (validNodeIds.contains(nodeId) && !restored.contains(nodeId)) {
                restored.add(nodeId);
            }
        }
        controller.setExpandedTreeNodes(restored);
    }

    private List<String> persistedExpandedTreeNodeIds() {
        if (latestSnapshot == null) {
            return new ArrayList<>(controller.expandedTreeNodes());
        }
        List<String> nodeIds = new ArrayList<>();
        appendPersistedExpandedTreeNodeIds(latestSnapshot.settingRoots(), nodeIds);
        appendPersistedExpandedTreeNodeIds(latestSnapshot.macroRoots(), nodeIds);
        return nodeIds;
    }

    private void appendPersistedExpandedTreeNodeIds(List<DashboardTreeNode> nodes, List<String> target) {
        for (DashboardTreeNode node : nodes) {
            if (node.expandable() && controller.isExpanded(node.id()) && !target.contains(node.id())) {
                target.add(node.id());
            }
            appendPersistedExpandedTreeNodeIds(node.children(), target);
        }
    }

    private Set<String> expandableTreeNodeIds(DashboardViewSnapshot snapshot) {
        Set<String> nodeIds = new HashSet<>();
        collectExpandableTreeNodeIds(snapshot.settingRoots(), nodeIds);
        collectExpandableTreeNodeIds(snapshot.macroRoots(), nodeIds);
        return nodeIds;
    }

    private void collectExpandableTreeNodeIds(List<DashboardTreeNode> nodes, Set<String> target) {
        for (DashboardTreeNode node : nodes) {
            if (node.expandable()) {
                target.add(node.id());
            }
            collectExpandableTreeNodeIds(node.children(), target);
        }
    }

    private void expandDefaults(List<DashboardTreeNode> nodes) {
        for (DashboardTreeNode node : nodes) {
            if (!node.expandable()) {
                continue;
            }
            controller.toggleExpanded(node.id());
            expandDefaults(node.children());
        }
    }

    private String stringProp(DashboardPrimitive node, String key) {
        Object value = node.props().get(key);
        return value != null ? String.valueOf(value) : "";
    }

    private int intProp(DashboardPrimitive node, String key, int fallback) {
        Object value = node.props().get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private boolean boolProp(DashboardPrimitive node, String key, boolean fallback) {
        Object value = node.props().get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private List<Double> frameHistorySnapshot() {
        return new ArrayList<>(frameHistory);
    }

    private void sampleFrameTime() {
        long now = System.nanoTime();
        if (lastFrameSampleNanos != 0L) {
            double ms = (now - lastFrameSampleNanos) / 1_000_000.0D;
            frameHistory.addLast(ms);
            while (frameHistory.size() > 90) {
                frameHistory.removeFirst();
            }
        }
        lastFrameSampleNanos = now;
    }

    private UiScaleContext currentScaleContext() {
        if (scaleContext != null && scaleContext.physicalViewport().width() == Math.max(1, width)
                && scaleContext.physicalViewport().height() == Math.max(1, height)) {
            return scaleContext;
        }
        scaleContext = UiScaleContext.of(dashboardScale, Math.max(1, width), Math.max(1, height));
        return scaleContext;
    }

    private double logicalMouseX(double physicalMouseX) {
        return currentScaleContext().toLogicalX(physicalMouseX);
    }

    private double logicalMouseY(double physicalMouseY) {
        return currentScaleContext().toLogicalY(physicalMouseY);
    }

    private int logicalWidth() {
        return currentScaleContext().logicalViewport().width();
    }

    private int logicalHeight() {
        return currentScaleContext().logicalViewport().height();
    }

    private int logicalUnitsForPhysicalPx(int physicalPx) {
        return currentScaleContext().logicalUnitsForPhysicalPx(physicalPx);
    }

    private double dragThresholdPx() {
        return 6.0D;
    }

    private float computeDashboardScale() {
        float widthFactor = width / 1920.0f;
        float heightFactor = height / 1080.0f;
        float designFactor = Mth.clamp(Math.min(widthFactor, heightFactor), 0.55f, 1.10f);
        return Mth.clamp(designFactor * controller.scaleMultiplier(), 0.32f, 1.80f);
    }

    private DashboardMetrics dashboardMetrics() {
        int logicalWidth = logicalWidth();
        int logicalHeight = logicalHeight();
        int shellInset = Math.max(6, Math.min(logicalWidth, logicalHeight) / 72);
        int columnGap = 5;
        int panelGap = 5;
        int minPanelWidth = 126;
        int minPanelHeight = 104;
        int minDiagnosticsHeight = 72;
        int diagnosticsDockedHeight = Math.max(92, Math.min(logicalHeight / 3, 180));
        float leftRatio = logicalWidth >= 1800 ? 0.37f : logicalWidth >= 1450 ? 0.41f : logicalWidth >= 1180 ? 0.45f : 0.49f;
        return new DashboardMetrics(shellInset, columnGap, panelGap, minPanelWidth, minPanelHeight,
                minDiagnosticsHeight, diagnosticsDockedHeight, leftRatio);
    }

    private int minPanelWidth(DashboardPanelId panelId) {
        DashboardMetrics metrics = dashboardMetrics();
        return panelId == DashboardPanelId.DIAGNOSTICS ? Math.round(metrics.minPanelWidth * 1.35f) : metrics.minPanelWidth;
    }

    private int minPanelHeight(DashboardPanelId panelId) {
        DashboardMetrics metrics = dashboardMetrics();
        return panelId == DashboardPanelId.DIAGNOSTICS ? metrics.minDiagnosticsHeight : metrics.minPanelHeight;
    }

    private record ScrollRegionInfo(UiRect bounds, int viewportHeight, int contentHeight) {
    }

    private record HorizontalScrollRegionInfo(UiRect bounds, int viewportWidth, int contentWidth) {
    }

    private record ScrollbarInfo(DashboardPrimitive track, DashboardPrimitive thumb, int viewportHeight, int contentHeight) {
    }

    private record HorizontalScrollbarInfo(DashboardPrimitive track, DashboardPrimitive thumb, int viewportWidth, int contentWidth) {
    }

    private record DashboardMetrics(int shellInset, int columnGap, int panelGap, int minPanelWidth, int minPanelHeight,
                                    int minDiagnosticsHeight, int diagnosticsDockedHeight, float leftRatio) {
    }

    private enum ResizeEdge {
        NONE(false, false, false, false),
        N(true, false, false, false),
        S(false, false, true, false),
        W(false, true, false, false),
        E(false, false, false, true),
        NW(true, true, false, false),
        NE(true, false, false, true),
        SW(false, true, true, false),
        SE(false, false, true, true);

        private final boolean north;
        private final boolean west;
        private final boolean south;
        private final boolean east;

        ResizeEdge(boolean north, boolean west, boolean south, boolean east) {
            this.north = north;
            this.west = west;
            this.south = south;
            this.east = east;
        }

        private static ResizeEdge byId(String value) {
            if (value == null || value.isBlank()) {
                return NONE;
            }
            return switch (value.trim().toUpperCase()) {
                case "N", "NORTH" -> N;
                case "S", "SOUTH" -> S;
                case "W", "WEST" -> W;
                case "E", "EAST" -> E;
                case "NW", "NORTHWEST" -> NW;
                case "NE", "NORTHEAST" -> NE;
                case "SW", "SOUTHWEST" -> SW;
                case "SE", "SOUTHEAST" -> SE;
                default -> NONE;
            };
        }
    }
}
