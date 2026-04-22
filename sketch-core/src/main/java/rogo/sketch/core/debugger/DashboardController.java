package rogo.sketch.core.debugger;

import rogo.sketch.core.pipeline.module.diagnostic.DiagnosticLevel;
import rogo.sketch.core.ui.geometry.UiRect;

import java.util.Collections;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DashboardController {
    private final Set<String> expandedTreeNodes = new HashSet<>();
    private final Set<Long> expandedLogEntries = new HashSet<>();
    private final EnumSet<DiagnosticLevel> diagnosticFilters = EnumSet.noneOf(DiagnosticLevel.class);
    private final EnumMap<DashboardPanelId, DashboardPanelState> panelStates = new EnumMap<>(DashboardPanelId.class);
    private final EnumMap<DashboardWindowId, Boolean> windowVisibility = new EnumMap<>(DashboardWindowId.class);
    private final EnumMap<DashboardWindowId, DashboardPanelId> windowPanels = new EnumMap<>(DashboardWindowId.class);
    private final EnumMap<DashboardPanelId, List<DashboardWindowId>> panelWindowOrder = new EnumMap<>(DashboardPanelId.class);
    private final EnumMap<DashboardPanelId, DashboardWindowId> activeWindows = new EnumMap<>(DashboardPanelId.class);
    private final java.util.Map<DashboardDockSlotId, Double> slotSizeRatios = new java.util.LinkedHashMap<>();
    private final java.util.List<DashboardPanelId> floatingOrder = new java.util.ArrayList<>();
    private DashboardTab activeTab = DashboardTab.MOD_SETTINGS;
    private String openTopbarMenuId;
    private int scaleLevel = 3;
    private boolean macroConstantsExpanded = true;
    private boolean memorySectionExpanded = true;
    private MetricsLayoutMode metricsLayoutMode = MetricsLayoutMode.AUTO;
    private long acknowledgedAlertSequence;
    private String openChoiceControlId;
    private DashboardPanelId dockPreviewPanelId;
    private DashboardDockSlotId dockPreviewSlotId;
    private DashboardPanelId tabDropPreviewPanelId;
    private double settingsScroll;
    private double metricsScroll;
    private double diagnosticsScroll;
    private double settingsHorizontalScroll;
    private double metricsHorizontalScroll;
    private double diagnosticsHorizontalScroll;

    public DashboardController() {
        for (DashboardPanelId panelId : DashboardPanelId.values()) {
            panelStates.put(panelId, new DashboardPanelState(panelId));
            panelWindowOrder.put(panelId, new ArrayList<>());
            floatingOrder.add(panelId);
        }
        for (DashboardWindowId windowId : DashboardWindowId.values()) {
            windowVisibility.put(windowId, Boolean.TRUE);
            windowPanels.put(windowId, windowId.defaultPanelId());
            panelWindowOrder.get(windowId.defaultPanelId()).add(windowId);
        }
        for (DashboardPanelId panelId : DashboardPanelId.values()) {
            activeWindows.put(panelId, firstVisibleWindow(panelId));
        }
    }

    public DashboardTab activeTab() {
        return activeTab;
    }

    public void setActiveTab(DashboardTab activeTab) {
        this.activeTab = activeTab;
    }

    public boolean isExpanded(String nodeId) {
        return expandedTreeNodes.contains(nodeId);
    }

    public void toggleExpanded(String nodeId) {
        if (!expandedTreeNodes.add(nodeId)) {
            expandedTreeNodes.remove(nodeId);
        }
    }

    public Set<String> expandedTreeNodes() {
        return Collections.unmodifiableSet(expandedTreeNodes);
    }

    public boolean isLogExpanded(long alertSequence) {
        return expandedLogEntries.contains(alertSequence);
    }

    public void toggleLogExpanded(long alertSequence) {
        if (!expandedLogEntries.add(alertSequence)) {
            expandedLogEntries.remove(alertSequence);
        }
    }

    public Set<Long> expandedLogEntries() {
        return Collections.unmodifiableSet(expandedLogEntries);
    }

    public String openTopbarMenuId() {
        return openTopbarMenuId;
    }

    public void setOpenTopbarMenuId(String openTopbarMenuId) {
        this.openTopbarMenuId = openTopbarMenuId;
    }

    public void toggleTopbarMenu(String menuId) {
        if (menuId == null || menuId.isBlank()) {
            openTopbarMenuId = null;
            return;
        }
        openTopbarMenuId = menuId.equals(openTopbarMenuId) ? null : menuId;
    }

    public int scaleLevel() {
        return scaleLevel;
    }

    public void setScaleLevel(int scaleLevel) {
        this.scaleLevel = Math.max(1, Math.min(5, scaleLevel));
    }

    public float scaleMultiplier() {
        return scaleMultiplierForLevel(scaleLevel);
    }

    public static float scaleMultiplierForLevel(int scaleLevel) {
        return switch (Math.max(1, Math.min(5, scaleLevel))) {
            case 1 -> 0.90f;
            case 2 -> 1.15f;
            case 3 -> 1.30f;
            case 4 -> 1.45f;
            default -> 1.60f;
        };
    }

    public boolean accepts(DiagnosticLevel level) {
        return diagnosticFilters.isEmpty() || diagnosticFilters.contains(level);
    }

    public void toggleDiagnosticFilter(DiagnosticLevel level) {
        if (!diagnosticFilters.add(level)) {
            diagnosticFilters.remove(level);
        }
    }

    public Set<DiagnosticLevel> diagnosticFilters() {
        return Collections.unmodifiableSet(diagnosticFilters);
    }

    public boolean macroConstantsExpanded() {
        return macroConstantsExpanded;
    }

    public void toggleMacroConstantsExpanded() {
        macroConstantsExpanded = !macroConstantsExpanded;
    }

    public boolean memorySectionExpanded() {
        return memorySectionExpanded;
    }

    public void toggleMemorySectionExpanded() {
        memorySectionExpanded = !memorySectionExpanded;
    }

    public MetricsLayoutMode metricsLayoutMode() {
        return metricsLayoutMode;
    }

    public void setMetricsLayoutMode(MetricsLayoutMode metricsLayoutMode) {
        this.metricsLayoutMode = metricsLayoutMode != null ? metricsLayoutMode : MetricsLayoutMode.AUTO;
    }

    public void cycleMetricsLayoutMode() {
        metricsLayoutMode = metricsLayoutMode.next();
    }

    public int resolveMetricColumns(int autoColumns) {
        return metricsLayoutMode.resolveColumns(autoColumns);
    }

    public long acknowledgedAlertSequence() {
        return acknowledgedAlertSequence;
    }

    public void acknowledgeAlertsUpTo(long acknowledgedAlertSequence) {
        this.acknowledgedAlertSequence = Math.max(this.acknowledgedAlertSequence, acknowledgedAlertSequence);
    }

    public String openChoiceControlId() {
        return openChoiceControlId;
    }

    public void setOpenChoiceControlId(String openChoiceControlId) {
        this.openChoiceControlId = openChoiceControlId;
    }

    public void closeChoiceDropdown() {
        this.openChoiceControlId = null;
    }

    public boolean isWindowVisible(DashboardWindowId windowId) {
        return windowId != null && Boolean.TRUE.equals(windowVisibility.get(windowId));
    }

    public void setWindowVisible(DashboardWindowId windowId, boolean visible) {
        if (windowId == null) {
            return;
        }
        windowVisibility.put(windowId, visible);
        DashboardPanelId panelId = windowPanelId(windowId);
        if (panelId != null && activeWindow(panelId) == windowId && !visible) {
            activeWindows.put(panelId, firstVisibleWindow(panelId));
        }
    }

    public void toggleWindowVisible(DashboardWindowId windowId) {
        setWindowVisible(windowId, !isWindowVisible(windowId));
    }

    public DashboardPanelId windowPanelId(DashboardWindowId windowId) {
        return windowId != null ? windowPanels.getOrDefault(windowId, windowId.defaultPanelId()) : null;
    }

    public List<DashboardWindowId> windowsForPanel(DashboardPanelId panelId) {
        List<DashboardWindowId> windows = panelWindowOrder.get(panelId);
        return windows != null ? List.copyOf(windows) : List.of();
    }

    public List<DashboardWindowId> visibleWindowsForPanel(DashboardPanelId panelId) {
        List<DashboardWindowId> result = new ArrayList<>();
        for (DashboardWindowId windowId : windowsForPanel(panelId)) {
            if (isWindowVisible(windowId)) {
                result.add(windowId);
            }
        }
        return result;
    }

    public DashboardWindowId activeWindow(DashboardPanelId panelId) {
        DashboardWindowId active = panelId != null ? activeWindows.get(panelId) : null;
        if (active != null && isWindowVisible(active) && windowPanelId(active) == panelId) {
            return active;
        }
        DashboardWindowId fallback = firstVisibleWindow(panelId);
        if (panelId != null) {
            activeWindows.put(panelId, fallback);
        }
        return fallback;
    }

    public void setActiveWindow(DashboardPanelId panelId, DashboardWindowId windowId) {
        if (panelId == null || windowId == null || windowPanelId(windowId) != panelId) {
            return;
        }
        activeWindows.put(panelId, windowId);
    }

    public void moveWindowToPanel(DashboardWindowId windowId, DashboardPanelId panelId, int index) {
        if (windowId == null || panelId == null) {
            return;
        }
        for (List<DashboardWindowId> order : panelWindowOrder.values()) {
            order.remove(windowId);
        }
        List<DashboardWindowId> target = panelWindowOrder.computeIfAbsent(panelId, ignored -> new ArrayList<>());
        int safeIndex = Math.max(0, Math.min(index, target.size()));
        target.add(safeIndex, windowId);
        windowPanels.put(windowId, panelId);
        activeWindows.put(panelId, windowId);
        for (DashboardPanelId candidate : DashboardPanelId.values()) {
            DashboardWindowId active = activeWindows.get(candidate);
            if (active == null || windowPanelId(active) != candidate || !isWindowVisible(active)) {
                activeWindows.put(candidate, firstVisibleWindow(candidate));
            }
        }
    }

    public void setPanelWindows(DashboardPanelId panelId, List<DashboardWindowId> windows) {
        if (panelId == null || windows == null) {
            return;
        }
        List<DashboardWindowId> order = panelWindowOrder.computeIfAbsent(panelId, ignored -> new ArrayList<>());
        order.clear();
        for (DashboardWindowId windowId : windows) {
            if (windowId == null || order.contains(windowId)) {
                continue;
            }
            order.add(windowId);
            windowPanels.put(windowId, panelId);
        }
        for (DashboardWindowId windowId : DashboardWindowId.values()) {
            if (windowPanelId(windowId) == panelId && !order.contains(windowId)) {
                windowPanels.put(windowId, windowId.defaultPanelId());
            }
        }
        activeWindows.put(panelId, firstVisibleWindow(panelId));
    }

    public void setWindowPanelId(DashboardWindowId windowId, DashboardPanelId panelId) {
        if (windowId == null || panelId == null) {
            return;
        }
        moveWindowToPanel(windowId, panelId, panelWindowOrder.getOrDefault(panelId, List.of()).size());
    }

    private DashboardWindowId firstVisibleWindow(DashboardPanelId panelId) {
        if (panelId == null) {
            return null;
        }
        for (DashboardWindowId windowId : panelWindowOrder.getOrDefault(panelId, List.of())) {
            if (isWindowVisible(windowId)) {
                return windowId;
            }
        }
        return null;
    }

    public DashboardPanelState panelState(DashboardPanelId panelId) {
        return panelId != null ? panelStates.get(panelId) : null;
    }

    public DashboardPanelMode panelMode(DashboardPanelId panelId) {
        DashboardPanelState state = panelState(panelId);
        return state != null ? state.mode() : DashboardPanelMode.DOCKED;
    }

    public boolean isDocked(DashboardPanelId panelId) {
        return panelMode(panelId) == DashboardPanelMode.DOCKED;
    }

    public boolean isFloating(DashboardPanelId panelId) {
        return panelMode(panelId) == DashboardPanelMode.FLOATING;
    }

    public void setPanelMode(DashboardPanelId panelId, DashboardPanelMode mode) {
        DashboardPanelState state = panelState(panelId);
        if (state == null) {
            return;
        }
        state.setMode(mode);
        if (state.mode() == DashboardPanelMode.FLOATING) {
            focusPanel(panelId);
        } else {
            ensureUniqueDockedSlot(panelId, state.dockedSlotId());
            if (dockPreviewPanelId == panelId) {
                dockPreviewPanelId = null;
            }
        }
    }

    public UiRect panelFloatingBounds(DashboardPanelId panelId) {
        DashboardPanelState state = panelState(panelId);
        return state != null ? state.floatingBounds() : new UiRect(0, 0, 0, 0);
    }

    public DashboardDockSlotId panelHomeSlotId(DashboardPanelId panelId) {
        DashboardPanelState state = panelState(panelId);
        return state != null ? state.homeSlotId() : null;
    }

    public void setPanelHomeSlotId(DashboardPanelId panelId, DashboardDockSlotId slotId) {
        DashboardPanelState state = panelState(panelId);
        if (state == null) {
            return;
        }
        state.setHomeSlotId(slotId);
        if (state.dockedSlotId() == null) {
            state.setDockedSlotId(slotId);
        }
    }

    public DashboardDockSlotId panelDockedSlotId(DashboardPanelId panelId) {
        DashboardPanelState state = panelState(panelId);
        return state != null ? state.dockedSlotId() : null;
    }

    public void setPanelDockedSlotId(DashboardPanelId panelId, DashboardDockSlotId slotId) {
        DashboardPanelState state = panelState(panelId);
        if (state == null) {
            return;
        }
        state.setDockedSlotId(slotId);
        if (state.mode() == DashboardPanelMode.DOCKED) {
            ensureUniqueDockedSlot(panelId, slotId);
        }
    }

    public void setPanelFloatingBounds(DashboardPanelId panelId, UiRect bounds) {
        DashboardPanelState state = panelState(panelId);
        if (state == null || bounds == null) {
            return;
        }
        state.setFloatingBounds(bounds);
    }

    public void clampFloatingPanelToScreen(DashboardPanelId panelId, int screenWidth, int screenHeight, int minWidth, int minHeight) {
        DashboardPanelState state = panelState(panelId);
        if (state == null) {
            return;
        }
        UiRect bounds = state.floatingBounds();
        int width = Math.max(minWidth, Math.min(bounds.width(), Math.max(minWidth, screenWidth)));
        int height = Math.max(minHeight, Math.min(bounds.height(), Math.max(minHeight, screenHeight)));
        int x = clampCoordinate(bounds.x(), width, screenWidth);
        int y = clampCoordinate(bounds.y(), height, screenHeight);
        state.setFloatingBounds(new UiRect(x, y, width, height));
    }

    private int clampCoordinate(int coordinate, int size, int limit) {
        if (limit <= size) {
            return 0;
        }
        return Math.max(0, Math.min(coordinate, limit - size));
    }

    public DashboardPanelId dockPreviewPanelId() {
        return dockPreviewPanelId;
    }

    public DashboardDockSlotId dockPreviewSlotId() {
        return dockPreviewSlotId;
    }

    public void setDockPreview(DashboardPanelId dockPreviewPanelId, DashboardDockSlotId dockPreviewSlotId) {
        if (dockPreviewPanelId == null) {
            clearDockPreview();
            return;
        }
        this.dockPreviewPanelId = dockPreviewPanelId;
        this.dockPreviewSlotId = dockPreviewSlotId;
    }

    public void clearDockPreview() {
        this.dockPreviewPanelId = null;
        this.dockPreviewSlotId = null;
    }

    public DashboardPanelId tabDropPreviewPanelId() {
        return tabDropPreviewPanelId;
    }

    public void setTabDropPreviewPanelId(DashboardPanelId tabDropPreviewPanelId) {
        this.tabDropPreviewPanelId = tabDropPreviewPanelId;
    }

    public void clearTabDropPreview() {
        this.tabDropPreviewPanelId = null;
    }

    private void ensureUniqueDockedSlot(DashboardPanelId panelId, DashboardDockSlotId slotId) {
        if (slotId == null) {
            return;
        }
        for (DashboardPanelId otherPanelId : DashboardPanelId.values()) {
            if (otherPanelId == panelId) {
                continue;
            }
            DashboardPanelState otherState = panelState(otherPanelId);
            if (otherState == null || otherState.mode() != DashboardPanelMode.DOCKED) {
                continue;
            }
            if (!slotId.equals(otherState.dockedSlotId())) {
                continue;
            }
            otherState.setMode(DashboardPanelMode.FLOATING);
            if (dockPreviewPanelId == otherPanelId) {
                clearDockPreview();
            }
        }
    }

    public double slotSizeRatio(DashboardDockSlotId slotId, double fallback) {
        if (slotId == null) {
            return fallback;
        }
        Double ratio = slotSizeRatios.get(slotId);
        return ratio != null ? ratio : fallback;
    }

    public void setSlotSizeRatio(DashboardDockSlotId slotId, double ratio) {
        if (slotId == null || Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            return;
        }
        slotSizeRatios.put(slotId, Math.max(0.0D, Math.min(1.0D, ratio)));
    }

    public DashboardPanelId focusedFloatingPanel() {
        for (int i = floatingOrder.size() - 1; i >= 0; i--) {
            DashboardPanelId panelId = floatingOrder.get(i);
            if (isFloating(panelId)) {
                return panelId;
            }
        }
        return null;
    }

    public void focusPanel(DashboardPanelId panelId) {
        if (panelId == null) {
            return;
        }
        floatingOrder.remove(panelId);
        floatingOrder.add(panelId);
    }

    public List<DashboardPanelId> floatingOrder() {
        return List.copyOf(floatingOrder);
    }

    public double settingsScroll() {
        return settingsScroll;
    }

    public void setSettingsScroll(double settingsScroll) {
        this.settingsScroll = Math.max(0.0D, settingsScroll);
    }

    public double metricsScroll() {
        return metricsScroll;
    }

    public void setMetricsScroll(double metricsScroll) {
        this.metricsScroll = Math.max(0.0D, metricsScroll);
    }

    public double diagnosticsScroll() {
        return diagnosticsScroll;
    }

    public void setDiagnosticsScroll(double diagnosticsScroll) {
        this.diagnosticsScroll = Math.max(0.0D, diagnosticsScroll);
    }

    public double settingsHorizontalScroll() {
        return settingsHorizontalScroll;
    }

    public void setSettingsHorizontalScroll(double settingsHorizontalScroll) {
        this.settingsHorizontalScroll = Math.max(0.0D, settingsHorizontalScroll);
    }

    public double metricsHorizontalScroll() {
        return metricsHorizontalScroll;
    }

    public void setMetricsHorizontalScroll(double metricsHorizontalScroll) {
        this.metricsHorizontalScroll = Math.max(0.0D, metricsHorizontalScroll);
    }

    public double diagnosticsHorizontalScroll() {
        return diagnosticsHorizontalScroll;
    }

    public void setDiagnosticsHorizontalScroll(double diagnosticsHorizontalScroll) {
        this.diagnosticsHorizontalScroll = Math.max(0.0D, diagnosticsHorizontalScroll);
    }
}
