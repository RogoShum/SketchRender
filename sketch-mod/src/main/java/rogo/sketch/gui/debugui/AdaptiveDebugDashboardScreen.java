
package rogo.sketch.gui.debugui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import rogo.sketch.core.dashboard.DashboardViewModelFactory;
import rogo.sketch.core.dashboard.DashboardViewSceneBuilder;
import rogo.sketch.core.dashboard.DashboardViewSnapshot;
import rogo.sketch.core.debugger.DashboardController;
import rogo.sketch.core.debugger.DashboardDataSource;
import rogo.sketch.core.debugger.DashboardTab;
import rogo.sketch.core.debugger.DiagnosticsPanelMode;
import rogo.sketch.core.debugger.ui.UiNode;
import rogo.sketch.core.debugger.ui.UiNodeType;
import rogo.sketch.core.debugger.ui.UiRect;
import rogo.sketch.core.debugger.ui.UiScene;
import rogo.sketch.core.pipeline.module.diagnostic.DiagnosticLevel;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeHost;
import rogo.sketch.core.ui.control.ChoiceSpec;
import rogo.sketch.core.ui.control.ControlSpec;
import rogo.sketch.core.ui.control.NumericKind;
import rogo.sketch.core.ui.control.NumericSpec;
import rogo.sketch.vanilla.PipelineUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class AdaptiveDebugDashboardScreen extends Screen {
    private static long sessionAcknowledgedAlertSequence;

    private final DashboardController controller = new DashboardController();
    private final DashboardViewModelFactory modelFactory = new DashboardViewModelFactory();
    private final DashboardViewSceneBuilder sceneBuilder = new DashboardViewSceneBuilder();
    private final AdaptiveDashboardRenderer renderer = new AdaptiveDashboardRenderer();
    private final ArrayDeque<Double> frameHistory = new ArrayDeque<>();
    private DashboardDataSource dataSource;
    private DashboardViewSnapshot latestSnapshot;
    private UiScene latestScene;
    private String draggingSliderControlId;
    private String draggingScrollArea;
    private int draggingScrollGrabOffset;
    private boolean draggingScrollHorizontal;
    private boolean expandedDefaults;
    private long lastFrameSampleNanos;
    private float dashboardScale = 1.0f;
    private String editingNumberControlId;
    private String editingNumberDraft = "";
    private Object editingNumberOriginalValue;

    public AdaptiveDebugDashboardScreen(Component title) {
        super(title);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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
        latestSnapshot = modelFactory.build(dataSource, PipelineUtil.pipeline().metricSnapshot(), PipelineUtil.pipeline().diagnosticsSnapshot());
        if (controller.diagnosticsPanelMode() == DiagnosticsPanelMode.EXPANDED) {
            acknowledgeVisibleAlerts();
        }
        if (!expandedDefaults) {
            for (var root : latestSnapshot.settingRoots()) {
                controller.toggleExpanded(root.id());
            }
            for (var root : latestSnapshot.macroRoots()) {
                controller.toggleExpanded(root.id());
            }
            expandedDefaults = true;
        }
        latestScene = sceneBuilder.build(latestSnapshot, controller, width, height, dashboardScale, editingNumberControlId, editingNumberDraft);
        if (clampScrolls()) {
            latestScene = sceneBuilder.build(latestSnapshot, controller, width, height, dashboardScale, editingNumberControlId, editingNumberDraft);
        }
        renderer.render(latestScene, controller, new AdaptiveMinecraftUiCanvas(guiGraphics), mouseX, mouseY);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (latestScene == null || dataSource == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        UiNode openChoiceNode = currentOpenChoiceNode();
        if (openChoiceNode != null) {
            for (AdaptiveDashboardRenderer.ChoiceHitBox hitBox : renderer.choiceHitBoxes(openChoiceNode, controller)) {
                if (hitBox.bounds().contains(mouseX, mouseY)) {
                    writeControl(openChoiceNode, hitBox.value());
                    controller.closeChoiceDropdown();
                    return true;
                }
            }
            if (!openChoiceNode.bounds().contains(mouseX, mouseY)) {
                controller.closeChoiceDropdown();
            }
        }

        UiNode hoveredNode = renderer.findTopNode(latestScene, mouseX, mouseY);
        if (editingNumberControlId != null) {
            UiNode editingNode = findControlNode(editingNumberControlId);
            boolean editingHit = editingNode != null && editingNode.bounds().contains(mouseX, mouseY);
            if (!editingHit) {
                commitNumberEdit();
            }
        }

        if (hoveredNode == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        switch (hoveredNode.type()) {
            case PANEL -> {
                return handlePanelClick(hoveredNode, mouseX, mouseY);
            }
            case TAB_BUTTON -> {
                controller.setActiveTab(hoveredNode.id().equals("tab-settings") ? DashboardTab.MOD_SETTINGS : DashboardTab.SHADER_MACROS);
                return true;
            }
            case TREE_GROUP -> {
                controller.toggleExpanded(hoveredNode.id());
                return true;
            }
            case TREE_CONTROL -> {
                return handleControlClick(hoveredNode, mouseX, mouseY);
            }
            case MACRO_SECTION_HEADER -> {
                controller.toggleMacroConstantsExpanded();
                return true;
            }
            case METRICS_LAYOUT_TOGGLE -> {
                controller.cycleMetricsLayoutMode();
                return true;
            }
            case DIAGNOSTIC_HEADER -> {
                setDiagnosticsMode(controller.diagnosticsPanelMode() == DiagnosticsPanelMode.COLLAPSED
                        ? DiagnosticsPanelMode.EXPANDED
                        : DiagnosticsPanelMode.COLLAPSED);
                return true;
            }
            case DIAGNOSTIC_FILTER -> {
                controller.toggleDiagnosticFilter(DiagnosticLevel.valueOf(String.valueOf(hoveredNode.props().get("level"))));
                return true;
            }
            case DIAGNOSTIC_STATE -> {
                setDiagnosticsMode(DiagnosticsPanelMode.valueOf(String.valueOf(hoveredNode.props().get("mode"))));
                return true;
            }
            default -> {
                return super.mouseClicked(mouseX, mouseY, button);
            }
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (latestScene == null) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (draggingSliderControlId != null) {
            UiNode node = findControlNode(draggingSliderControlId);
            if (node == null) {
                draggingSliderControlId = null;
                return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }
            updateSliderValue(node, mouseX);
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
                setHorizontalScrollFromThumb(draggingScrollArea, scrollbar, mouseX - draggingScrollGrabOffset - scrollbar.track().bounds().x());
                return true;
            }
            ScrollbarInfo scrollbar = scrollbarInfo(draggingScrollArea);
            if (scrollbar == null) {
                draggingScrollArea = null;
                return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }
            setScrollFromThumb(draggingScrollArea, scrollbar, mouseY - draggingScrollGrabOffset - scrollbar.track().bounds().y());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSliderControlId = null;
        draggingScrollArea = null;
        draggingScrollHorizontal = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        String scrollArea = scrollAreaAt(mouseX, mouseY);
        if (scrollArea != null) {
            setScroll(scrollArea, currentScroll(scrollArea) + (-delta * Math.max(18.0D, 22.0D * dashboardScale)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editingNumberControlId == null) {
            return super.charTyped(codePoint, modifiers);
        }
        UiNode node = findControlNode(editingNumberControlId);
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
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean handleControlClick(UiNode node, double mouseX, double mouseY) {
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
                if (controller.openChoiceControlId() != null && controller.openChoiceControlId().equals(controlId)) {
                    controller.closeChoiceDropdown();
                } else {
                    controller.setOpenChoiceControlId(controlId);
                }
                return true;
            }
        }
        return false;
    }

    private boolean handlePanelClick(UiNode node, double mouseX, double mouseY) {
        String role = stringProp(node, "role");
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

    private void updateSliderValue(UiNode node, double mouseX) {
        ControlSpec controlSpec = node.props().get("controlSpec") instanceof ControlSpec spec ? spec : null;
        if (controlSpec == null || controlSpec.numericSpec() == null) {
            return;
        }
        NumericSpec numericSpec = controlSpec.numericSpec();
        UiRect control = controlBounds(node.bounds());
        int valueWidth = Math.max(48, Math.round(54 * dashboardScale));
        int trackStart = control.x() + Math.max(6, Math.round(8 * dashboardScale));
        int trackWidth = control.width() - valueWidth - Math.max(16, Math.round(20 * dashboardScale));
        double normalized = (mouseX - trackStart) / Math.max(1.0D, trackWidth);
        normalized = Mth.clamp(normalized, 0.0D, 1.0D);
        double value = numericSpec.minValue() + (numericSpec.maxValue() - numericSpec.minValue()) * normalized;
        double snapped = snapNumericValue(value, numericSpec);
        if (numericSpec.numericKind() == NumericKind.INTEGER) {
            writeControl(node, (int) Math.round(snapped));
        } else {
            writeControl(node, (float) snapped);
        }
    }

    private void writeControl(UiNode node, Object value) {
        Object controlId = node.props().get("controlId");
        if (controlId == null || dataSource == null) {
            return;
        }
        dataSource.controlAccessor().writeValue(String.valueOf(controlId), value, dataSource.runtimeHost().settingRegistry(), null);
    }

    private void beginNumberEdit(UiNode node) {
        editingNumberControlId = String.valueOf(node.props().get("controlId"));
        editingNumberOriginalValue = node.props().get("value");
        ControlSpec controlSpec = node.props().get("controlSpec") instanceof ControlSpec spec ? spec : null;
        NumericSpec numericSpec = controlSpec != null ? controlSpec.numericSpec() : null;
        editingNumberDraft = numericSpec != null ? formatNumericForEditing(numericSpec, editingNumberOriginalValue) : String.valueOf(editingNumberOriginalValue);
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
        UiNode node = findControlNode(editingNumberControlId);
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

    private void setDiagnosticsMode(DiagnosticsPanelMode mode) {
        controller.setDiagnosticsPanelMode(mode);
        if (mode == DiagnosticsPanelMode.EXPANDED) {
            acknowledgeVisibleAlerts();
        }
    }

    private void acknowledgeVisibleAlerts() {
        if (latestSnapshot == null) {
            return;
        }
        controller.acknowledgeAlertsUpTo(latestSnapshot.latestAlertSequence());
        sessionAcknowledgedAlertSequence = Math.max(sessionAcknowledgedAlertSequence, controller.acknowledgedAlertSequence());
    }

    private UiNode currentOpenChoiceNode() {
        if (latestScene == null || controller.openChoiceControlId() == null) {
            return null;
        }
        return findControlNode(controller.openChoiceControlId());
    }

    private UiNode findControlNode(String controlId) {
        if (latestScene == null) {
            return null;
        }
        for (UiNode node : latestScene.nodes()) {
            if (node.type() == UiNodeType.TREE_CONTROL && controlId.equals(String.valueOf(node.props().get("controlId")))) {
                return node;
            }
        }
        return null;
    }
    private UiRect controlBounds(UiRect row) {
        int minWidth = Math.max(132, Math.round(146 * dashboardScale));
        int width = Math.max(minWidth, Math.min(Math.round(row.width() * 0.45f), Math.round(176 * dashboardScale)));
        return new UiRect(row.right() - width, row.y(), width - Math.max(4, Math.round(6 * dashboardScale)), row.height());
    }

    private String scrollAreaAt(double mouseX, double mouseY) {
        if (latestScene == null) {
            return null;
        }
        List<UiNode> nodes = latestScene.nodes();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            UiNode node = nodes.get(i);
            if (node.type() != UiNodeType.PANEL || !"scroll-region".equals(stringProp(node, "role"))) {
                continue;
            }
            if (node.bounds().contains(mouseX, mouseY)) {
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
            case "diagnostics-x" -> controller.diagnosticsHorizontalScroll();
            default -> 0.0D;
        };
    }

    private void assignScroll(String area, double value) {
        switch (area) {
            case "settings" -> controller.setSettingsScroll(value);
            case "metrics" -> controller.setMetricsScroll(value);
            case "diagnostics" -> controller.setDiagnosticsScroll(value);
            case "diagnostics-x" -> controller.setDiagnosticsHorizontalScroll(value);
            default -> {
            }
        }
    }

    private ScrollRegionInfo scrollRegionInfo(String area) {
        if (latestScene == null) {
            return null;
        }
        for (UiNode node : latestScene.nodes()) {
            if (node.type() == UiNodeType.PANEL && "scroll-region".equals(stringProp(node, "role")) && area.equals(stringProp(node, "scrollArea"))) {
                return new ScrollRegionInfo(node.bounds(), intProp(node, "viewportHeight", node.bounds().height()), intProp(node, "contentHeight", node.bounds().height()));
            }
        }
        return null;
    }

    private HorizontalScrollRegionInfo horizontalScrollRegionInfo(String area) {
        if (latestScene == null) {
            return null;
        }
        for (UiNode node : latestScene.nodes()) {
            if (node.type() == UiNodeType.PANEL && "scroll-region-x".equals(stringProp(node, "role")) && area.equals(stringProp(node, "scrollArea"))) {
                return new HorizontalScrollRegionInfo(node.bounds(), intProp(node, "viewportWidth", node.bounds().width()), intProp(node, "contentWidth", node.bounds().width()));
            }
        }
        return null;
    }

    private ScrollbarInfo scrollbarInfo(String area) {
        if (latestScene == null) {
            return null;
        }
        UiNode track = null;
        UiNode thumb = null;
        for (UiNode node : latestScene.nodes()) {
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
        return new ScrollbarInfo(track, thumb, intProp(track, "viewportHeight", track.bounds().height()), intProp(track, "contentHeight", track.bounds().height()));
    }

    private HorizontalScrollbarInfo horizontalScrollbarInfo(String area) {
        if (latestScene == null) {
            return null;
        }
        UiNode track = null;
        UiNode thumb = null;
        for (UiNode node : latestScene.nodes()) {
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
        return new HorizontalScrollbarInfo(track, thumb, intProp(track, "viewportWidth", track.bounds().width()), intProp(track, "contentWidth", track.bounds().width()));
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

    private String stringProp(UiNode node, String key) {
        Object value = node.props().get(key);
        return value != null ? String.valueOf(value) : "";
    }

    private int intProp(UiNode node, String key, int fallback) {
        Object value = node.props().get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private boolean boolProp(UiNode node, String key, boolean fallback) {
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

    private float computeDashboardScale() {
        Minecraft minecraft = Minecraft.getInstance();
        double guiScale = minecraft.getWindow().getGuiScale();
        float widthFactor = width / 1920.0f;
        float heightFactor = height / 1080.0f;
        float designFactor = Math.min(widthFactor, heightFactor);
        float guiFactor = (float) Mth.clamp(guiScale / 3.0D, 0.85D, 1.35D);
        return Mth.clamp(designFactor * guiFactor, 0.75f, 1.35f);
    }

    private record ScrollRegionInfo(UiRect bounds, int viewportHeight, int contentHeight) {
    }

    private record HorizontalScrollRegionInfo(UiRect bounds, int viewportWidth, int contentWidth) {
    }

    private record ScrollbarInfo(UiNode track, UiNode thumb, int viewportHeight, int contentHeight) {
    }

    private record HorizontalScrollbarInfo(UiNode track, UiNode thumb, int viewportWidth, int contentWidth) {
    }
}
