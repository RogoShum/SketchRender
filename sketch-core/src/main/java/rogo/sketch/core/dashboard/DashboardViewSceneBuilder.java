package rogo.sketch.core.dashboard;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.control.ChoicePresentation;
import rogo.sketch.core.ui.control.ChoiceSpec;
import rogo.sketch.core.ui.control.ChoiceOptionSpec;
import rogo.sketch.core.ui.control.ControlKind;
import rogo.sketch.core.ui.control.ControlSpec;
import rogo.sketch.core.debugger.DashboardController;
import rogo.sketch.core.debugger.DashboardDockResizeEdge;
import rogo.sketch.core.debugger.DashboardDockResizeHandle;
import rogo.sketch.core.debugger.DashboardDockSlotId;
import rogo.sketch.core.debugger.DashboardPanelId;
import rogo.sketch.core.debugger.DashboardTab;
import rogo.sketch.core.debugger.DashboardTexturePreview;
import rogo.sketch.core.debugger.DashboardTreeNode;
import rogo.sketch.core.debugger.DashboardDockSlotSpec;
import rogo.sketch.core.debugger.DashboardWindowId;
import rogo.sketch.core.debugger.DashboardWorkspaceLayout;
import rogo.sketch.core.debugger.DashboardWorkspaceProfile;
import rogo.sketch.core.debugger.DashboardWorkspaceProfiles;
import rogo.sketch.core.debugger.ui.UiNodeType;
import rogo.sketch.core.pipeline.module.diagnostic.DiagnosticLevel;
import rogo.sketch.core.ui.frame.UiFrame;
import rogo.sketch.core.ui.frame.UiInteractionSurface;
import rogo.sketch.core.ui.frame.UiLayer;
import rogo.sketch.core.ui.frame.UiPaintPass;
import rogo.sketch.core.ui.frame.UiPrimitive;
import rogo.sketch.core.ui.frame.TexturePrimitive;
import rogo.sketch.core.ui.geometry.UiScaleContext;
import rogo.sketch.core.ui.geometry.UiRect;
import rogo.sketch.core.ui.input.CursorHint;
import rogo.sketch.core.ui.input.HitRegion;
import rogo.sketch.core.ui.input.InputActionId;
import rogo.sketch.core.ui.input.RectHitShape;
import rogo.sketch.core.ui.text.UiMeasuredTextBlock;
import rogo.sketch.core.ui.text.UiText;
import rogo.sketch.core.ui.text.UiTextMetrics;
import rogo.sketch.core.ui.text.UiTextLayouts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DashboardViewSceneBuilder {
    private final DashboardWorkspaceProfile workspaceProfile;
    private final UiTextMetrics textMetrics;

    public DashboardViewSceneBuilder() {
        this(DashboardWorkspaceProfiles.dashboardDefault(), null);
    }

    public DashboardViewSceneBuilder(DashboardWorkspaceProfile workspaceProfile) {
        this(workspaceProfile, null);
    }

    public DashboardViewSceneBuilder(DashboardWorkspaceProfile workspaceProfile, UiTextMetrics textMetrics) {
        this.workspaceProfile = workspaceProfile != null ? workspaceProfile : DashboardWorkspaceProfiles.dashboardDefault();
        this.textMetrics = textMetrics;
    }

    public UiFrame build(DashboardViewSnapshot snapshot, DashboardController controller, int screenWidth, int screenHeight, float uiScale) {
        return build(snapshot, controller, UiScaleContext.of(uiScale, screenWidth, screenHeight), null, null);
    }

    public UiFrame build(DashboardViewSnapshot snapshot, DashboardController controller, int screenWidth, int screenHeight, float uiScale,
                         @Nullable String editingNumberControlId, @Nullable String editingDraftValue) {
        return build(snapshot, controller, UiScaleContext.of(uiScale, screenWidth, screenHeight), editingNumberControlId, editingDraftValue);
    }

    public UiFrame build(DashboardViewSnapshot snapshot, DashboardController controller, UiScaleContext scaleContext) {
        return build(snapshot, controller, scaleContext, null, null);
    }

    public UiFrame build(DashboardViewSnapshot snapshot, DashboardController controller, UiScaleContext scaleContext,
                         @Nullable String editingNumberControlId, @Nullable String editingDraftValue) {
        UiScaleContext resolvedScaleContext = scaleContext != null ? scaleContext : UiScaleContext.of(1.0f, 1, 1);
        Metrics metrics = new Metrics(resolvedScaleContext, resolvedLineHeight());
        UiRect logicalViewport = resolvedScaleContext.logicalViewport();
        FrameAssembly assembly = new FrameAssembly(resolvedScaleContext, controller);
        assembly.add(new DashboardPrimitive("background", UiNodeType.PANEL, UiLayer.BACKGROUND, assembly.nextOrder(),
                new UiRect(0, 0, logicalViewport.width(), logicalViewport.height()), null,
                Map.of("fill", 0x64101922, "role", "backdrop")));

        appendTopbar(assembly, controller, logicalViewport.width(), metrics);
        DashboardWorkspaceLayout layout = shiftedLayout(
                workspaceProfile.layout(logicalViewport.width(), Math.max(1, logicalViewport.height() - metrics.topbarHeight), 1.0f, controller),
                0,
                metrics.topbarHeight);
        appendHomeSlotMetadata(assembly, layout, controller, metrics);
        appendSlotResizeHandles(assembly, layout, metrics);
        appendSlotPreview(assembly, layout, controller, metrics);

        appendDockedOrPlaceholder(assembly, DashboardPanelId.SETTINGS, layout, slotBounds(layout, controller, DashboardPanelId.SETTINGS), controller, metrics,
                snapshot, editingNumberControlId, editingDraftValue);
        appendDockedOrPlaceholder(assembly, DashboardPanelId.METRICS, layout, slotBounds(layout, controller, DashboardPanelId.METRICS), controller, metrics,
                snapshot, editingNumberControlId, editingDraftValue);
        appendDockedOrPlaceholder(assembly, DashboardPanelId.DIAGNOSTICS, layout, slotBounds(layout, controller, DashboardPanelId.DIAGNOSTICS), controller, metrics,
                snapshot, editingNumberControlId, editingDraftValue);

        for (DashboardPanelId panelId : controller.floatingOrder()) {
            if (!controller.isFloating(panelId)) {
                continue;
            }
            UiRect floatingBounds = resolveFloatingBounds(controller, panelId, slotBounds(layout, controller, panelId));
            buildPanel(assembly, panelId, snapshot, controller, floatingBounds, true, metrics, editingNumberControlId, editingDraftValue);
        }
        return assembly.build();
    }

    private int resolvedLineHeight() {
        return textMetrics != null ? Math.max(1, textMetrics.lineHeight()) : 9;
    }

    private void appendDockedOrPlaceholder(FrameAssembly assembly, DashboardPanelId panelId, DashboardWorkspaceLayout layout, UiRect slotRect, DashboardController controller,
                                           Metrics metrics, DashboardViewSnapshot snapshot,
                                           @Nullable String editingNumberControlId, @Nullable String editingDraftValue) {
        if (controller.isDocked(panelId)) {
            buildPanel(assembly, panelId, snapshot, controller, slotRect, false, metrics, editingNumberControlId, editingDraftValue);
            return;
        }
        DashboardDockSlotId slotId = resolvedSlotId(controller, panelId);
        DashboardDockSlotSpec slotSpec = layout.slotSpec(slotId);
        assembly.add(new DashboardPrimitive("panel-slot-empty/" + (slotId != null ? slotId.value() : panelId.id()), UiNodeType.PANEL, UiLayer.PANELS, assembly.nextOrder(),
                slotRect, null,
                slotProps(slotSpec, Map.of("fill", 0x16192531, "border", 0x50324150, "role", "panel-slot-empty",
                        "slotId", slotId != null ? slotId.value() : "", "scale", metrics.uiScale))));
    }

    private void buildPanel(FrameAssembly assembly, DashboardPanelId panelId, DashboardViewSnapshot snapshot, DashboardController controller,
                            UiRect panelRect, boolean floating, Metrics metrics,
                            @Nullable String editingNumberControlId, @Nullable String editingDraftValue) {
        switch (panelId) {
            case SETTINGS -> buildSettingsPanel(assembly, snapshot, controller, panelRect, floating, metrics, editingNumberControlId, editingDraftValue);
            case METRICS -> buildMetricsPanel(assembly, snapshot, controller, panelRect, floating, metrics);
            case DIAGNOSTICS -> buildDiagnosticsPanel(assembly, snapshot, controller, panelRect, floating, metrics);
        }
    }

    private void buildSettingsPanel(FrameAssembly assembly, DashboardViewSnapshot snapshot, DashboardController controller,
                                    UiRect panelRect, boolean floating, Metrics metrics,
                                    @Nullable String editingNumberControlId, @Nullable String editingDraftValue) {
        UiLayer panelLayer = floating ? UiLayer.OVERLAY : UiLayer.PANELS;
        UiLayer contentLayer = floating ? UiLayer.OVERLAY : UiLayer.CONTROLS;
        appendPanelBackground(assembly, DashboardPanelId.SETTINGS, panelRect, panelLayer, floating,
                0xB6151C28, 0xA935465E, metrics);

        UiRect header = headerRect(panelRect, metrics);
        Map<String, Object> headerProps = new LinkedHashMap<>();
        headerProps.put("title", "debug.dashboard.configuration");
        headerProps.put("fill", 0xD61A2330);
        headerProps.put("border", 0x7A37485F);
        headerProps.put("role", "panel-header");
        headerProps.put("panelId", DashboardPanelId.SETTINGS.id());
        headerProps.put("floating", floating);
        headerProps.put("interactive", true);
        headerProps.put("scale", metrics.uiScale);
        assembly.add(new DashboardPrimitive("settings-header", UiNodeType.HEADER, contentLayer, assembly.nextOrder(),
                header, null, headerProps));
        appendModeToggle(assembly, DashboardPanelId.SETTINGS, header, contentLayer, floating, metrics);
        if (floating) {
            appendResizeHandles(assembly, DashboardPanelId.SETTINGS, panelRect, contentLayer, metrics);
        }

        int tabsBottom = appendWindowTabs(assembly, DashboardPanelId.SETTINGS, controller, panelRect, header.bottom(), contentLayer, metrics);
        appendWindowContent(assembly, DashboardPanelId.SETTINGS, snapshot, controller, panelRect, tabsBottom,
                metrics, contentLayer, editingNumberControlId, editingDraftValue);
    }

    private void appendWindowContent(FrameAssembly assembly, DashboardPanelId panelId, DashboardViewSnapshot snapshot,
                                     DashboardController controller, UiRect panelRect, int tabsBottom, Metrics metrics, UiLayer contentLayer,
                                     @Nullable String editingNumberControlId, @Nullable String editingDraftValue) {
        DashboardWindowId activeWindow = controller.activeWindow(panelId);
        boolean consoleActive = activeWindow == DashboardWindowId.CONSOLE;
        boolean metricsLike = activeWindow == DashboardWindowId.METRICS || activeWindow == DashboardWindowId.MEMORY;
        if (metricsLike) {
            int contentWidth = Math.max(metrics.minMetricColumnWidth, panelRect.width() - metrics.innerInset * 2 - metrics.scrollbarGutter);
            int columns = resolveMetricColumns(controller, contentWidth, metrics);
            assembly.add(new DashboardPrimitive("metrics-layout-toggle/" + panelId.id(), UiNodeType.METRICS_LAYOUT_TOGGLE, contentLayer, assembly.nextOrder(),
                    new UiRect(panelRect.right() - metrics.innerInset - metrics.headerButtonSize - metrics.smallGap - metrics.layoutToggleWidth,
                            panelRect.y() + metrics.layoutToggleYOffset,
                            metrics.layoutToggleWidth,
                            metrics.layoutToggleHeight), null,
                    panelProps(panelId, Map.of("layoutMode", controller.metricsLayoutMode().name(),
                            "columns", columns, "scale", metrics.uiScale))));
        }

        int filtersY = tabsBottom + metrics.smallGap;
        int viewportY = tabsBottom + metrics.sectionGap;
        if (consoleActive) {
            int filterX = panelRect.x() + metrics.innerInset;
            for (DiagnosticLevel level : DiagnosticLevel.values()) {
                assembly.add(new DashboardPrimitive("diag-filter/" + panelId.id() + "/" + level.name(), UiNodeType.DIAGNOSTIC_FILTER, contentLayer, assembly.nextOrder(),
                        new UiRect(filterX, filtersY, metrics.filterWidth, metrics.filterHeight), null,
                        panelProps(panelId, Map.of("level", level.name(),
                                "active", controller.diagnosticFilters().contains(level), "scale", metrics.uiScale))));
                filterX += metrics.filterWidth + metrics.smallGap;
            }
            viewportY = filtersY + metrics.filterHeight + metrics.sectionGap;
        }
        viewportY = Math.min(viewportY, Math.max(panelRect.y() + metrics.headerHeight, panelRect.bottom() - metrics.innerInset - 1));

        int viewportWidth = Math.max(metrics.minDiagnosticsClipHeight, panelRect.width() - metrics.innerInset * 2 - metrics.scrollbarGutter);
        int contentWidth = consoleActive ? Math.max(viewportWidth, estimateDiagnosticsContentWidth(snapshot, controller, metrics)) : viewportWidth;
        boolean needsHorizontalScrollbar = consoleActive && contentWidth > viewportWidth;
        int viewportHeight = Math.max(1, panelRect.bottom() - viewportY - metrics.innerInset);
        if (needsHorizontalScrollbar) {
            viewportHeight = Math.max(1, viewportHeight - metrics.horizontalScrollbarHeight - metrics.smallGap);
        }

        UiRect viewport = new UiRect(panelRect.x() + metrics.innerInset, viewportY, viewportWidth, viewportHeight);
        UiRect track = verticalTrackRect(viewport, panelRect, metrics);
        String verticalArea = verticalScrollArea(panelId);
        String horizontalArea = horizontalScrollArea(panelId);
        double scroll = verticalScroll(controller, panelId);
        double horizontalScroll = horizontalScroll(controller, panelId);
        int y = viewport.y() - (int) Math.round(scroll);

        if (activeWindow == DashboardWindowId.SETTINGS || activeWindow == DashboardWindowId.SHADER_MACROS) {
            List<DashboardTreeNode> roots = activeWindow == DashboardWindowId.SHADER_MACROS ? snapshot.macroRoots() : snapshot.settingRoots();
            for (DashboardTreeNode root : roots) {
                y = appendTree(assembly, panelId, root, 0, viewport, y, controller, editingNumberControlId, editingDraftValue, metrics, contentLayer);
            }
        } else if (activeWindow == DashboardWindowId.MEMORY) {
            int columns = resolveMetricColumns(controller, viewport.width(), metrics);
            y = appendMemorySection(assembly, panelId, snapshot.memorySection(), viewport, y, controller, columns, metrics.gap, metrics, contentLayer);
        } else if (activeWindow == DashboardWindowId.FRAME_CAPTURE) {
            y = appendFrameCaptureSection(assembly, panelId, snapshot, viewport, y, metrics, contentLayer);
        } else if (activeWindow == DashboardWindowId.METRICS) {
            int columns = resolveMetricColumns(controller, viewport.width(), metrics);
            int columnGap = metrics.gap;
            int columnWidth = columns <= 1 ? viewport.width() : Math.max(metrics.minMetricColumnWidth, (viewport.width() - columnGap * (columns - 1)) / columns);
            y = appendMetricsOverviewSection(assembly, panelId, snapshot, controller, viewport, y, columns, columnWidth, columnGap, metrics, contentLayer);
        } else if (activeWindow == DashboardWindowId.CONSOLE) {
            y = appendConsoleSection(assembly, panelId, snapshot, controller, viewport, y, contentWidth, horizontalScroll, metrics, contentLayer);
        }

        int contentHeight = Math.max(viewport.height(), y + (int) Math.round(scroll) - viewport.y());
        appendVerticalScrollMetadata(assembly, panelId, verticalArea, viewport, contentHeight, metrics, contentLayer);
        appendVerticalScrollbar(assembly, panelId, verticalArea, viewport, track, panelRect,
                scroll, contentHeight, metrics, contentLayer);

        if (needsHorizontalScrollbar) {
            UiRect horizontalTrack = new UiRect(viewport.x(), viewport.bottom() + metrics.smallGap, viewport.width(), metrics.horizontalScrollbarHeight);
            appendHorizontalScrollMetadata(assembly, panelId, horizontalArea, horizontalTrack,
                    viewport.width(), contentWidth, metrics, contentLayer);
            appendHorizontalScrollbar(assembly, panelId, horizontalArea, horizontalTrack, panelRect,
                    horizontalScroll, viewport.width(), contentWidth, metrics, contentLayer);
        }
    }

    private int appendTree(FrameAssembly assembly, DashboardPanelId panelId, DashboardTreeNode node, int depth, UiRect viewport, int startY, DashboardController controller,
                           @Nullable String editingNumberControlId, @Nullable String editingDraftValue, Metrics metrics, UiLayer layer) {
        int indent = depth * metrics.indentStep;
        int x = viewport.x() + metrics.contentPadding + indent;
        int width = Math.max(metrics.minControlWidth, viewport.width() - metrics.contentPadding * 2 - indent);
        if (node.group()) {
            assembly.add(new DashboardPrimitive(node.id(), UiNodeType.TREE_GROUP, layer, assembly.nextOrder(),
                    new UiRect(x, startY, width, metrics.groupHeight), viewport,
                    panelProps(panelId, Map.of("title", node.displayKey(),
                            "summary", Objects.toString(node.summaryKey(), ""), "expanded", controller.isExpanded(node.id()),
                            "scale", metrics.uiScale))));
            startY += metrics.groupHeight + metrics.smallGap;
            if (controller.isExpanded(node.id())) {
                for (DashboardTreeNode child : node.children()) {
                    startY = appendTree(assembly, panelId, child, depth + 1, viewport, startY, controller, editingNumberControlId, editingDraftValue, metrics, layer);
                }
            }
            return startY + metrics.groupGap;
        }

        Map<String, Object> props = new LinkedHashMap<>();
        boolean blocked = !node.blockedByDisplayPath().isEmpty();
        props.put("title", node.displayKey());
        props.put("summary", Objects.toString(node.summaryKey(), ""));
        props.put("detail", Objects.toString(node.disabledDetailKey() != null ? node.disabledDetailKey() : node.detailKey(), ""));
        props.put("enabled", node.enabled());
        props.put("active", node.active());
        props.put("blocked", blocked);
        props.put("blockedByNodeId", Objects.toString(node.blockedByNodeId(), ""));
        props.put("blockedByDisplayPath", node.blockedByDisplayPath());
        props.put("controlId", node.controlId());
        props.put("controlSpec", node.controlSpec());
        props.put("value", node.value());
        props.put("macros", node.macroNames());
        props.put("expandable", node.expandable());
        props.put("expanded", node.expandable() && controller.isExpanded(node.id()));
        props.put("editing", node.controlId() != null && node.controlId().equals(editingNumberControlId));
        props.put("draftValue", node.controlId() != null && node.controlId().equals(editingNumberControlId) ? Objects.toString(editingDraftValue, "") : "");
        props.put("scale", metrics.uiScale);
        props.put("panelId", panelId.id());
        int rowHeight = dynamicTextRowHeight(List.of(node.displayKey(), Objects.toString(node.summaryKey(), "")),
                width, metrics.rowHeight, metrics);
        assembly.add(new DashboardPrimitive(node.id(), UiNodeType.TREE_CONTROL, layer, assembly.nextOrder(),
                new UiRect(x, startY, width, rowHeight), viewport, props));
        if (isDropdownChoiceOpen(node, controller)) {
            appendChoicePopup(assembly, panelId, node, new UiRect(x, startY, width, rowHeight), metrics);
        }
        startY += rowHeight + metrics.smallGap;
        if (node.expandable() && controller.isExpanded(node.id())) {
            for (DashboardTreeNode child : node.children()) {
                startY = appendTree(assembly, panelId, child, depth + 1, viewport, startY, controller, editingNumberControlId, editingDraftValue, metrics, layer);
            }
            startY += metrics.groupGap;
        }
        return startY;
    }

    private boolean isDropdownChoiceOpen(DashboardTreeNode node, DashboardController controller) {
        if (node == null || controller == null || node.controlId() == null || !node.controlId().equals(controller.openChoiceControlId())) {
            return false;
        }
        ControlSpec controlSpec = node.controlSpec();
        if (controlSpec == null || controlSpec.kind() != ControlKind.CHOICE) {
            return false;
        }
        ChoiceSpec choiceSpec = controlSpec.choiceSpec();
        if (choiceSpec == null) {
            return false;
        }
        return !(choiceSpec.presentation() == ChoicePresentation.SEGMENTED
                || (choiceSpec.presentation() == ChoicePresentation.AUTO && choiceSpec.options().size() <= 3));
    }

    private void appendChoicePopup(FrameAssembly assembly, DashboardPanelId panelId, DashboardTreeNode node, UiRect rowBounds, Metrics metrics) {
        ControlSpec controlSpec = node.controlSpec();
        ChoiceSpec choiceSpec = controlSpec != null ? controlSpec.choiceSpec() : null;
        if (choiceSpec == null || choiceSpec.options().isEmpty()) {
            return;
        }
        UiRect popupBounds = DashboardControlGeometry.choiceDropdownBounds(rowBounds, controlSpec, metrics.uiScale,
                choiceSpec.options().size(), assembly.screenBounds);
        String controlId = Objects.toString(node.controlId(), "");
        String surfaceOwnerId = "choice:" + controlId;
        assembly.add(new DashboardPrimitive("choice-popup/" + controlId, UiNodeType.POPUP_PANEL, UiLayer.OVERLAY, assembly.nextOrder(),
                popupBounds, null,
                panelProps(panelId, Map.of("role", "choice-dropdown-panel", "interactive", true,
                        "controlId", controlId,
                        "surfaceKind", "popup",
                        "surfaceOwnerId", surfaceOwnerId,
                        "scale", metrics.uiScale))));
        int rowHeight = DashboardControlGeometry.choiceDropdownRowHeight(metrics.uiScale);
        int y = popupBounds.y();
        for (int i = 0; i < choiceSpec.options().size(); i++) {
            ChoiceOptionSpec option = choiceSpec.options().get(i);
            assembly.add(new DashboardPrimitive("choice-popup/" + controlId + "/" + i, UiNodeType.POPUP_MENU_ITEM, UiLayer.OVERLAY, assembly.nextOrder(),
                    new UiRect(popupBounds.x(), y, popupBounds.width(), rowHeight), null,
                    panelProps(panelId, Map.of("role", "choice-option",
                            "interactive", true,
                            "controlId", controlId,
                            "optionValue", option.value(),
                            "value", node.value(),
                            "label", option.displayKey(),
                            "surfaceKind", "popup",
                            "surfaceOwnerId", surfaceOwnerId,
                            "scale", metrics.uiScale))));
            y += rowHeight;
        }
    }

    private void buildMetricsPanel(FrameAssembly assembly, DashboardViewSnapshot snapshot, DashboardController controller,
                                   UiRect panelRect, boolean floating, Metrics metrics) {
        UiLayer panelLayer = floating ? UiLayer.OVERLAY : UiLayer.PANELS;
        UiLayer contentLayer = floating ? UiLayer.OVERLAY : UiLayer.CONTROLS;
        appendPanelBackground(assembly, DashboardPanelId.METRICS, panelRect, panelLayer, floating,
                0xB8141A26, 0xA9314157, metrics);

        UiRect header = headerRect(panelRect, metrics);
        Map<String, Object> headerProps = new LinkedHashMap<>();
        headerProps.put("title", "debug.dashboard.metrics");
        headerProps.put("fill", 0xD61A2330);
        headerProps.put("border", 0x7A37485F);
        headerProps.put("role", "panel-header");
        headerProps.put("panelId", DashboardPanelId.METRICS.id());
        headerProps.put("floating", floating);
        headerProps.put("interactive", true);
        headerProps.put("scale", metrics.uiScale);
        assembly.add(new DashboardPrimitive("metrics-header", UiNodeType.HEADER, contentLayer, assembly.nextOrder(),
                header, null, headerProps));
        appendModeToggle(assembly, DashboardPanelId.METRICS, header, contentLayer, floating, metrics);
        if (floating) {
            appendResizeHandles(assembly, DashboardPanelId.METRICS, panelRect, contentLayer, metrics);
        }
        int tabsBottom = appendWindowTabs(assembly, DashboardPanelId.METRICS, controller, panelRect, header.bottom(), contentLayer, metrics);
        appendWindowContent(assembly, DashboardPanelId.METRICS, snapshot, controller, panelRect, tabsBottom,
                metrics, contentLayer, null, null);
    }

    private int appendSummaryMetricRows(
            FrameAssembly assembly,
            DashboardPanelId panelId,
            List<DashboardSummaryMetric> metricsSnapshot,
            UiRect viewport,
            int startY,
            int columns,
            int columnWidth,
            int columnGap,
            int rowHeight,
            Metrics metrics,
            UiLayer contentLayer,
            String idPrefix) {
        int y = startY;
        for (int index = 0; index < metricsSnapshot.size(); ) {
            int rowStartY = y;
            int rowEndIndex = Math.min(metricsSnapshot.size(), index + columns);
            int resolvedRowHeight = rowHeight;
            for (int measureIndex = index; measureIndex < rowEndIndex; measureIndex++) {
                resolvedRowHeight = Math.max(resolvedRowHeight, summaryMetricHeight(metricsSnapshot.get(measureIndex), columnWidth, rowHeight, metrics));
            }
            for (int column = 0; column < columns && index < metricsSnapshot.size(); column++, index++) {
                DashboardSummaryMetric metric = metricsSnapshot.get(index);
                int itemX = viewport.x() + column * (columnWidth + columnGap);
                UiMeasuredTextBlock titleLayout = measureTextBlock(metric.labelKey(), Math.max(24, columnWidth - metrics.innerInset * 2), metrics);
                UiMeasuredTextBlock valueLayout = measureTextBlock(
                        metric.unitText().isEmpty() ? metric.valueText() : metric.valueText() + " " + metric.unitText(),
                        Math.max(24, Math.round(columnWidth * 0.42f)), metrics);
                Map<String, Object> props = new LinkedHashMap<>();
                props.put("title", metric.labelKey());
                props.put("value", metric.valueText());
                props.put("unit", metric.unitText());
                props.put("accent", metric.accentColor());
                props.put("detail", metric.detailKey());
                props.put("scale", metrics.uiScale);
                props.put("mode", "summary-row");
                props.put("titleLayout", titleLayout);
                props.put("valueLayout", valueLayout);
                props.put("titleLines", titleLayout.lines());
                props.put("valueLines", valueLayout.lines());
                assembly.add(new DashboardPrimitive(idPrefix + metric.id(), UiNodeType.METRIC_CARD, contentLayer, assembly.nextOrder(),
                        new UiRect(itemX, rowStartY, columnWidth, resolvedRowHeight), viewport,
                        panelProps(panelId, props)));
            }
            y += resolvedRowHeight + metrics.smallGap;
        }
        return y;
    }

    private int appendMemorySection(
            FrameAssembly assembly,
            DashboardPanelId panelId,
            DashboardMemorySection memorySection,
            UiRect viewport,
            int startY,
            DashboardController controller,
            int metricColumns,
            int columnGap,
            Metrics metrics,
            UiLayer contentLayer) {
        if (memorySection == null || memorySection.isEmpty()) {
            return startY;
        }

        int y = startY + metrics.sectionGap;
        assembly.add(new DashboardPrimitive("memory-section-header", UiNodeType.MACRO_SECTION_HEADER, contentLayer, assembly.nextOrder(),
                new UiRect(viewport.x(), y, viewport.width(), metrics.groupHeight), viewport,
                panelProps(panelId, Map.of(
                        "title", memorySection.titleKey(),
                        "expanded", controller.memorySectionExpanded(),
                        "scale", metrics.uiScale,
                        "sectionId", "memory"))));
        y += metrics.groupHeight + metrics.smallGap;

        if (!controller.memorySectionExpanded()) {
            return y;
        }

        int summaryColumns = Math.max(1, Math.min(metricColumns, 2));
        int summaryColumnWidth = summaryColumns <= 1
                ? viewport.width()
                : Math.max(metrics.minMetricColumnWidth, (viewport.width() - columnGap * (summaryColumns - 1)) / summaryColumns);
        y = appendSummaryMetricRows(
                assembly,
                panelId,
                memorySection.summaryMetrics(),
                viewport,
                y,
                summaryColumns,
                summaryColumnWidth,
                columnGap,
                metrics.summaryRowHeight,
                metrics,
                contentLayer,
                "memory-summary/");

        for (DashboardMemoryDomainMetric domainMetric : memorySection.domainMetrics()) {
            DashboardMemoryRowLayout memoryLayout = measureMemoryDomainRow(domainMetric, viewport.width(), metrics);
            Map<String, Object> domainProps = new LinkedHashMap<>();
            domainProps.put("title", domainMetric.labelKey());
            domainProps.put("live", domainMetric.liveText());
            domainProps.put("reserved", domainMetric.reservedText());
            domainProps.put("peak", domainMetric.peakText());
            domainProps.put("budget", domainMetric.budgetText());
            domainProps.put("usagePercent", domainMetric.usagePercentText());
            domainProps.put("peakPercent", domainMetric.peakPercentText());
            domainProps.put("fragmentation", domainMetric.fragmentationText());
            domainProps.put("usageRatio", domainMetric.usageRatio());
            domainProps.put("peakRatio", domainMetric.peakRatio());
            domainProps.put("accent", domainMetric.accentColor());
            domainProps.put("detail", domainMetric.detailKey());
            domainProps.put("memoryLayout", memoryLayout);
            domainProps.put("titleLayout", memoryLayout.titleLayout());
            domainProps.put("reservedLayout", memoryLayout.reservedLayout());
            domainProps.put("detailLayout", memoryLayout.detailLayout());
            domainProps.put("tailLayout", memoryLayout.tailLayout());
            domainProps.put("scale", metrics.uiScale);
            domainProps.put("mode", "memory-domain-row");
            int rowHeight = memoryLayout.rowHeight();
            assembly.add(new DashboardPrimitive(domainMetric.id(), UiNodeType.METRIC_CARD, contentLayer, assembly.nextOrder(),
                    new UiRect(viewport.x(), y, viewport.width(), rowHeight), viewport,
                    panelProps(panelId, domainProps)));
            y += rowHeight + metrics.sectionGap;
        }

        assembly.add(new DashboardPrimitive("memory-chart", UiNodeType.BAR_CHART, contentLayer, assembly.nextOrder(),
                new UiRect(viewport.x(), y, viewport.width(), metrics.chartHeight), viewport,
                panelProps(panelId, Map.of(
                        "bars", memorySection.timelineValues(),
                        "title", memorySection.timelineTitleKey(),
                        "threshold", memorySection.timelineThreshold(),
                        "scale", metrics.uiScale,
                        "mode", "memory-chart"))));
        return y + metrics.chartHeight + metrics.sectionGap;
    }

    private int appendFrameCaptureSection(FrameAssembly assembly, DashboardPanelId panelId, DashboardViewSnapshot snapshot, UiRect viewport,
                                          int startY, Metrics metrics, UiLayer contentLayer) {
        int y = startY;
        assembly.add(new DashboardPrimitive("frame-capture-button", UiNodeType.CAPTURE_BUTTON, contentLayer, assembly.nextOrder(),
                new UiRect(viewport.right() - metrics.headerButtonSize, y,
                        metrics.headerButtonSize, metrics.headerButtonSize), viewport,
                panelProps(panelId, Map.of(
                        "role", "frame-capture",
                        "interactive", true,
                        "detail", "debug.dashboard.capture_frame.detail",
                        "scale", metrics.uiScale))));
        y += metrics.headerButtonSize + metrics.sectionGap;
        var capture = snapshot.frameCaptureSnapshot();
        List<DashboardSummaryMetric> captureSummary = List.of(
                new DashboardSummaryMetric("frame-capture-stages", "debug.dashboard.frame_capture.stages",
                        String.valueOf(capture.stages().size()), "", 0xFF60A5FA, ""),
                new DashboardSummaryMetric("frame-capture-bindings", "debug.dashboard.frame_capture.bindings",
                        String.valueOf(capture.resourceBindings().size()), "", 0xFF34D399, ""),
                new DashboardSummaryMetric("frame-capture-render-state", "debug.dashboard.frame_capture.render_state",
                        capture.renderState().resourceBindingStamp() != null ? capture.renderState().resourceBindingStamp().toString() : "-",
                        "", 0xFFF59E0B, ""));
        y = appendSummaryMetricRows(assembly, panelId, captureSummary, viewport, y, 1, viewport.width(), 0,
                metrics.summaryRowHeight, metrics, contentLayer, "frame-capture/");
        for (var stage : capture.stages()) {
            String label = stage.stageId() != null ? stage.stageId().toString() : "unknown";
            String summary = "packets " + stage.packetCount()
                    + " / draw " + stage.drawPacketCount()
                    + " / states " + stage.states().size();
            UiMeasuredTextBlock titleLayout = measureTextBlock(label, Math.max(24, viewport.width() - metrics.innerInset * 2), metrics);
            UiMeasuredTextBlock summaryLayout = measureTextBlock(summary, Math.max(24, viewport.width() - metrics.innerInset * 2), metrics);
            int lineCount = Math.max(titleLayout.lineCount(), summaryLayout.lineCount());
            int rowHeight = Math.max(metrics.constantRowHeight,
                    metrics.innerInset + lineCount * metrics.lineHeight + Math.max(0, lineCount - 1) * metrics.textLineGap + metrics.innerInset);
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("title", label);
            props.put("summary", summary);
            props.put("accent", 0xFF60A5FA);
            props.put("titleLayout", titleLayout);
            props.put("summaryLayout", summaryLayout);
            props.put("scale", metrics.uiScale);
            props.put("mode", "capture-stage-row");
            assembly.add(new DashboardPrimitive("frame-capture-stage/" + label, UiNodeType.METRIC_CARD, contentLayer, assembly.nextOrder(),
                    new UiRect(viewport.x(), y, viewport.width(), rowHeight), viewport,
                    panelProps(panelId, props)));
            y += rowHeight + metrics.sectionGap;
        }
        return y;
    }

    private int appendMetricsOverviewSection(FrameAssembly assembly, DashboardPanelId panelId, DashboardViewSnapshot snapshot,
                                             DashboardController controller, UiRect viewport, int startY, int columns, int columnWidth,
                                             int columnGap, Metrics metrics, UiLayer contentLayer) {
        int y = appendSummaryMetricRows(
                assembly,
                panelId,
                snapshot.summaryMetrics(),
                viewport,
                startY,
                columns,
                columnWidth,
                columnGap,
                metrics.summaryRowHeight,
                metrics,
                contentLayer,
                "metric/");

        if (!snapshot.ratioMetrics().isEmpty()) {
            y += metrics.sectionGap;
        }
        for (DashboardRatioMetric ratioMetric : snapshot.ratioMetrics()) {
            assembly.add(new DashboardPrimitive("ratio/" + ratioMetric.id(), UiNodeType.METRIC_CARD, contentLayer, assembly.nextOrder(),
                    new UiRect(viewport.x(), y, viewport.width(), metrics.ratioRowHeight), viewport,
                    panelProps(panelId, Map.of("title", ratioMetric.labelKey(), "mode", "ratio-row",
                            "hidden", ratioMetric.hiddenCount(), "visible", ratioMetric.visibleCount(),
                            "total", ratioMetric.totalCount(), "ratio", ratioMetric.hiddenRatio(),
                            "accent", ratioMetric.accentColor(), "detail", ratioMetric.detailKey(), "scale", metrics.uiScale))));
            y += metrics.ratioRowHeight + metrics.sectionGap;
        }

        y = appendTexturePreviews(assembly, panelId, snapshot.texturePreviews(), viewport, y, metrics, contentLayer);

        assembly.add(new DashboardPrimitive("frame-chart", UiNodeType.BAR_CHART, contentLayer, assembly.nextOrder(),
                new UiRect(viewport.x(), y, viewport.width(), metrics.chartHeight), viewport,
                panelProps(panelId, Map.of("bars", snapshot.frameTimeHistory(),
                        "title", "debug.dashboard.frame_history", "threshold", 33.0D,
                        "scale", metrics.uiScale, "mode", "frame-chart"))));
        y += metrics.chartHeight + metrics.sectionGap;

        assembly.add(new DashboardPrimitive("macro-constants-header", UiNodeType.MACRO_SECTION_HEADER, contentLayer, assembly.nextOrder(),
                new UiRect(viewport.x(), y, viewport.width(), metrics.groupHeight), viewport,
                panelProps(panelId, Map.of("title", "debug.dashboard.macro_constants",
                        "expanded", controller.macroConstantsExpanded(), "scale", metrics.uiScale,
                        "sectionId", "macro-constants"))));
        y += metrics.groupHeight + metrics.smallGap;

        if (controller.macroConstantsExpanded()) {
            for (DashboardMacroConstantView constant : snapshot.macroConstants()) {
                int rowHeight = dynamicTextRowHeight(List.of(constant.name(), constant.sourceText(), constant.value()), viewport.width(), metrics.constantRowHeight, metrics);
                assembly.add(new DashboardPrimitive("macro-constant/" + constant.name() + "/" + constant.sourceText(),
                        UiNodeType.MACRO_CONSTANT_ROW, contentLayer, assembly.nextOrder(),
                        new UiRect(viewport.x(), y, viewport.width(), rowHeight), viewport,
                        panelProps(panelId, Map.of("name", constant.name(), "value", constant.value(),
                                "flag", constant.flag(), "source", constant.sourceText(), "type", constant.typeText(),
                                "detail", constant.detailText(), "scale", metrics.uiScale, "mode", "macro-constant-row"))));
                y += rowHeight + metrics.smallGap;
            }
        }
        return y;
    }

    private int appendTexturePreviews(FrameAssembly assembly, DashboardPanelId panelId, List<DashboardTexturePreview> previews,
                                      UiRect viewport, int startY, Metrics metrics, UiLayer contentLayer) {
        if (previews.isEmpty()) {
            return startY;
        }
        int y = startY + metrics.sectionGap;
        for (DashboardTexturePreview preview : previews) {
            float aspect = preview.width() / (float) Math.max(1, preview.height());
            int maxImageWidth = Math.max(1, viewport.width() - metrics.innerInset * 2);
            int imageHeight = Math.max(metrics.texturePreviewMinHeight,
                    Math.min(metrics.texturePreviewMaxHeight, Math.round(maxImageWidth / Math.max(0.1F, aspect))));
            int imageWidth = Math.min(maxImageWidth, Math.max(1, Math.round(imageHeight * Math.max(0.1F, aspect))));
            if (imageWidth >= maxImageWidth) {
                imageWidth = maxImageWidth;
                imageHeight = Math.max(1, Math.round(imageWidth / Math.max(0.1F, aspect)));
            }
            int cardHeight = metrics.innerInset + metrics.lineHeight + metrics.textLineGap + metrics.lineHeight
                    + metrics.smallGap + imageHeight + metrics.innerInset;
            UiRect card = new UiRect(viewport.x(), y, viewport.width(), cardHeight);
            assembly.add(new DashboardPrimitive("texture-preview/" + preview.id(), UiNodeType.PANEL, contentLayer, assembly.nextOrder(),
                    card, viewport,
                    panelProps(panelId, Map.of("role", "texture-preview-card",
                            "title", preview.titleKey(),
                            "detail", preview.detailText(),
                            "accent", preview.accentColor(),
                            "fill", 0x5A18222E,
                            "border", 0x7A324150,
                            "scale", metrics.uiScale))));
            UiRect image = new UiRect(card.x() + metrics.innerInset + Math.max(0, (maxImageWidth - imageWidth) / 2),
                    card.y() + metrics.innerInset + metrics.lineHeight * 2 + metrics.textLineGap + metrics.smallGap,
                    imageWidth,
                    imageHeight);
            assembly.add(new TexturePrimitive(contentLayer, assembly.nextOrder(),
                    preview.texture(), image, preview.uv(), 0xFFFFFFFF, viewport,
                    UiPaintPass.TEXTURE,
                    contentLayer == UiLayer.OVERLAY
                            ? UiInteractionSurface.floatingPanel(panelId.id(), 0)
                            : UiInteractionSurface.content(panelId.id())));
            y += cardHeight + metrics.sectionGap;
        }
        return y;
    }

    private int appendConsoleSection(FrameAssembly assembly, DashboardPanelId panelId, DashboardViewSnapshot snapshot,
                                     DashboardController controller, UiRect viewport, int startY, int contentWidth, double horizontalScroll,
                                     Metrics metrics, UiLayer contentLayer) {
        int y = startY;
        int visibleCount = 0;
        int totalVisibleCount = 0;
        for (var line : snapshot.diagnostics()) {
            if (controller.accepts(line.level())) {
                totalVisibleCount++;
            }
        }
        for (var line : snapshot.diagnostics()) {
            if (!controller.accepts(line.level())) {
                continue;
            }
            visibleCount++;
            boolean expanded = controller.isLogExpanded(line.alertSequence());
            UiMeasuredTextBlock messageLayout = expanded
                    ? measureTextBlock(line.message(), Math.max(32, viewport.width() - metrics.innerInset * 2), metrics)
                    : measureSingleLineText(line.message(), metrics);
            String detailText = Objects.toString(line.stackTrace() != null && !line.stackTrace().isBlank() ? line.stackTrace() : line.stackPreview(), "");
            UiMeasuredTextBlock detailLayout = expanded && !detailText.isBlank()
                    ? measureTextBlock(detailText, Math.max(32, viewport.width() - metrics.innerInset * 2), metrics)
                    : new UiMeasuredTextBlock(List.of(), metrics.lineHeight, metrics.textLineGap, 0, 0);
            List<String> messageLines = messageLayout.lines();
            List<String> detailLines = expanded ? detailLayout.lines() : List.of();
            int bodyLines = 1 + messageLayout.lineCount() + (expanded ? detailLayout.lineCount() : 0);
            int lineHeight = expanded
                    ? Math.max(metrics.logLineHeight, metrics.innerInset + bodyLines * messageLayout.lineHeight()
                    + Math.max(0, bodyLines - 1) * messageLayout.lineGap() + metrics.innerInset)
                    : metrics.logLineHeight;
            if (expanded) {
                lineHeight = Math.max(lineHeight, metrics.filterHeight + metrics.smallGap * 2);
            }
            Map<String, Object> logProps = new LinkedHashMap<>();
            logProps.put("alertSequence", line.alertSequence());
            logProps.put("time", line.timeText());
            logProps.put("level", line.level().name());
            logProps.put("module", line.moduleId());
            logProps.put("thread", line.threadName());
            logProps.put("message", line.message());
            logProps.put("messageLayout", messageLayout);
            logProps.put("messageLines", messageLines);
            logProps.put("detailLayout", detailLayout);
            logProps.put("detailLines", detailLines);
            logProps.put("fullText", line.fullText());
            logProps.put("expanded", expanded);
            logProps.put("repeat", line.repeatCount());
            logProps.put("detail", detailText);
            logProps.put("scale", metrics.uiScale);
            assembly.add(new DashboardPrimitive("log/" + panelId.id() + "/" + visibleCount, UiNodeType.LOG_LINE, contentLayer, assembly.nextOrder(),
                    new UiRect(viewport.x() - (int) Math.round(horizontalScroll), y, contentWidth, lineHeight),
                    viewport,
                    panelProps(panelId, logProps)));
            if (expanded) {
                assembly.add(new DashboardPrimitive("log-copy/" + panelId.id() + "/" + line.alertSequence(), UiNodeType.LOG_COPY_BUTTON, contentLayer, assembly.nextOrder(),
                        new UiRect(viewport.right() - metrics.copyButtonWidth, y + metrics.smallGap,
                                metrics.copyButtonWidth, metrics.filterHeight), viewport,
                        panelProps(panelId, Map.of("alertSequence", line.alertSequence(),
                                "fullText", line.fullText(), "label", "debug.dashboard.console.copy", "scale", metrics.uiScale))));
            }
            y += lineHeight;
            if (visibleCount < totalVisibleCount) {
                y += metrics.smallGap;
            }
        }
        return y;
    }

    private void buildDiagnosticsPanel(FrameAssembly assembly, DashboardViewSnapshot snapshot, DashboardController controller,
                                       UiRect panelRect, boolean floating, Metrics metrics) {
        UiLayer panelLayer = floating ? UiLayer.OVERLAY : UiLayer.PANELS;
        UiLayer contentLayer = floating ? UiLayer.OVERLAY : UiLayer.CONTROLS;
        appendPanelBackground(assembly, DashboardPanelId.DIAGNOSTICS, panelRect, panelLayer, floating,
                0xE6111721, 0xCC3A5268, metrics);

        boolean unreadAlerts = snapshot.latestAlertSequence() > controller.acknowledgedAlertSequence();
        UiRect header = headerRect(panelRect, metrics);
        Map<String, Object> headerProps = new LinkedHashMap<>();
        headerProps.put("title", "debug.dashboard.diagnostics");
        headerProps.put("unreadAlerts", unreadAlerts);
        headerProps.put("warningCount", snapshot.warningCount());
        headerProps.put("errorCount", snapshot.errorCount());
        headerProps.put("role", "panel-header");
        headerProps.put("panelId", DashboardPanelId.DIAGNOSTICS.id());
        headerProps.put("floating", floating);
        headerProps.put("interactive", true);
        headerProps.put("scale", metrics.uiScale);
        UiRect modeToggleRect = modeToggleRect(header, metrics);
        int badgeGap = metrics.smallGap + 2;
        int badgeLaneRight = modeToggleRect.x() - metrics.smallGap;
        int reservedBadgeWidth = diagnosticsBadgeLaneWidth(snapshot.warningCount(), snapshot.errorCount(), badgeGap, metrics);
        int actionLaneLeft = Math.max(header.x() + metrics.innerInset, badgeLaneRight - reservedBadgeWidth);
        headerProps.put("badgeGap", badgeGap);
        headerProps.put("badgeLaneRight", badgeLaneRight);
        headerProps.put("actionLaneLeft", actionLaneLeft);
        headerProps.put("titleRight", Math.max(header.x() + metrics.innerInset, actionLaneLeft - metrics.smallGap));
        assembly.add(new DashboardPrimitive("diagnostics-header", UiNodeType.DIAGNOSTIC_HEADER, contentLayer, assembly.nextOrder(),
                header, null, headerProps));
        appendModeToggle(assembly, DashboardPanelId.DIAGNOSTICS, header, contentLayer, floating, metrics);
        if (floating) {
            appendResizeHandles(assembly, DashboardPanelId.DIAGNOSTICS, panelRect, contentLayer, metrics);
        }
        int tabsBottom = appendWindowTabs(assembly, DashboardPanelId.DIAGNOSTICS, controller, panelRect, header.bottom(), contentLayer, metrics);
        appendWindowContent(assembly, DashboardPanelId.DIAGNOSTICS, snapshot, controller, panelRect, tabsBottom,
                metrics, contentLayer, null, null);
    }

    private int resolveMetricColumns(DashboardController controller, int contentWidth, Metrics metrics) {
        int requestedColumns = controller.resolveMetricColumns(metrics.autoMetricColumns);
        int fittingColumns = 1;
        for (int candidate = 1; candidate <= 3; candidate++) {
            int requiredWidth = candidate * metrics.minMetricColumnWidth + (candidate - 1) * metrics.gap;
            if (requiredWidth <= contentWidth) {
                fittingColumns = candidate;
            }
        }
        return Math.max(1, Math.min(requestedColumns, fittingColumns));
    }

    private String verticalScrollArea(DashboardPanelId panelId) {
        return switch (panelId) {
            case SETTINGS -> "settings";
            case METRICS -> "metrics";
            case DIAGNOSTICS -> "diagnostics";
        };
    }

    private String horizontalScrollArea(DashboardPanelId panelId) {
        return verticalScrollArea(panelId) + "-x";
    }

    private double verticalScroll(DashboardController controller, DashboardPanelId panelId) {
        return switch (panelId) {
            case SETTINGS -> controller.settingsScroll();
            case METRICS -> controller.metricsScroll();
            case DIAGNOSTICS -> controller.diagnosticsScroll();
        };
    }

    private double horizontalScroll(DashboardController controller, DashboardPanelId panelId) {
        return switch (panelId) {
            case SETTINGS -> controller.settingsHorizontalScroll();
            case METRICS -> controller.metricsHorizontalScroll();
            case DIAGNOSTICS -> controller.diagnosticsHorizontalScroll();
        };
    }

    private void appendTopbar(FrameAssembly assembly, DashboardController controller, int screenWidth, Metrics metrics) {
        UiRect bar = new UiRect(0, 0, screenWidth, metrics.topbarHeight);
        assembly.add(new DashboardPrimitive("topbar", UiNodeType.TOPBAR, UiLayer.CONTROLS, assembly.nextOrder(),
                bar, null, Map.of("fill", 0xE80B111A, "border", 0x80324458, "role", "topbar", "scale", metrics.uiScale)));
        int x = metrics.innerInset;
        x = appendTopbarButton(assembly, "topbar/scale", "debug.dashboard.topbar.scale", "scale", x, metrics, controller);
        appendTopbarButton(assembly, "topbar/windows", "debug.dashboard.topbar.windows", "windows", x, metrics, controller);
        if ("scale".equals(controller.openTopbarMenuId())) {
            int menuX = metrics.innerInset;
            int menuY = bar.bottom() + 2;
            int menuHeight = metrics.topbarMenuRowHeight * 5;
            assembly.add(new DashboardPrimitive("topbar/menu/scale", UiNodeType.POPUP_PANEL, UiLayer.OVERLAY, assembly.nextOrder(),
                    new UiRect(menuX, menuY, metrics.topbarMenuWidth, menuHeight), null,
                    Map.of("role", "topbar-popup-panel", "interactive", true,
                            "surfaceKind", "popup", "surfaceOwnerId", "topbar:scale", "scale", metrics.uiScale)));
            for (int level = 1; level <= 5; level++) {
                assembly.add(new DashboardPrimitive("topbar/scale/" + level, UiNodeType.TOPBAR_MENU_ITEM, UiLayer.OVERLAY, assembly.nextOrder(),
                        new UiRect(menuX, menuY, metrics.topbarMenuWidth, metrics.topbarMenuRowHeight), null,
                        Map.of("role", "scale-option", "menuId", "scale", "scaleLevel", level,
                                "active", controller.scaleLevel() == level,
                                "label", "debug.dashboard.scale.level." + level,
                                "surfaceKind", "popup", "surfaceOwnerId", "topbar:scale",
                                "scale", metrics.uiScale)));
                menuY += metrics.topbarMenuRowHeight;
            }
        } else if ("windows".equals(controller.openTopbarMenuId())) {
            int menuX = metrics.innerInset + metrics.topbarButtonWidth + metrics.smallGap;
            int menuY = bar.bottom() + 2;
            int menuHeight = metrics.topbarMenuRowHeight * DashboardWindowId.values().length;
            assembly.add(new DashboardPrimitive("topbar/menu/windows", UiNodeType.POPUP_PANEL, UiLayer.OVERLAY, assembly.nextOrder(),
                    new UiRect(menuX, menuY, metrics.topbarMenuWidth, menuHeight), null,
                    Map.of("role", "topbar-popup-panel", "interactive", true,
                            "surfaceKind", "popup", "surfaceOwnerId", "topbar:windows", "scale", metrics.uiScale)));
            for (DashboardWindowId windowId : DashboardWindowId.values()) {
                assembly.add(new DashboardPrimitive("topbar/window/" + windowId.id(), UiNodeType.TOPBAR_MENU_ITEM, UiLayer.OVERLAY, assembly.nextOrder(),
                        new UiRect(menuX, menuY, metrics.topbarMenuWidth, metrics.topbarMenuRowHeight), null,
                        Map.of("role", "window-toggle", "menuId", "windows", "windowId", windowId.id(),
                                "active", controller.isWindowVisible(windowId), "label", windowId.titleKey(),
                                "multiSelect", true,
                                "surfaceKind", "popup", "surfaceOwnerId", "topbar:windows",
                                "scale", metrics.uiScale)));
                menuY += metrics.topbarMenuRowHeight;
            }
        }
    }

    private int appendTopbarButton(FrameAssembly assembly, String id, String label, String menuId, int x, Metrics metrics, DashboardController controller) {
        assembly.add(new DashboardPrimitive(id, UiNodeType.TOPBAR, UiLayer.CONTROLS, assembly.nextOrder(),
                new UiRect(x, metrics.smallGap, metrics.topbarButtonWidth, metrics.topbarHeight - metrics.smallGap * 2), null,
                Map.of("fill", 0x50131D27, "border", 0x78324458, "role", "topbar-button",
                        "menuId", menuId, "label", label, "active", menuId.equals(controller.openTopbarMenuId()), "scale", metrics.uiScale)));
        return x + metrics.topbarButtonWidth + metrics.smallGap;
    }

    private int appendWindowTabs(FrameAssembly assembly, DashboardPanelId panelId, DashboardController controller,
                                 UiRect panelRect, int startY, UiLayer layer, Metrics metrics) {
        List<DashboardWindowId> windows = controller.visibleWindowsForPanel(panelId);
        int tabY = startY + metrics.smallGap;
        if (windows.isEmpty()) {
            assembly.add(new DashboardPrimitive("window-tab-empty/" + panelId.id(), UiNodeType.WINDOW_TAB, layer, assembly.nextOrder(),
                    new UiRect(panelRect.x() + metrics.innerInset, tabY,
                            Math.max(1, panelRect.width() - metrics.innerInset * 2), metrics.tabHeight), null,
                    panelProps(panelId, Map.of("title", "debug.dashboard.window.empty",
                            "active", false, "disabled", true, "scale", metrics.uiScale))));
            return tabY + metrics.tabHeight;
        }
        int x = panelRect.x() + metrics.innerInset;
        int right = panelRect.right() - metrics.innerInset - metrics.headerButtonSize - metrics.smallGap;
        int availableTabsWidth = Math.max(1, right - x - metrics.gap * Math.max(0, windows.size() - 1));
        int tabWidth = Math.max(1, Math.min(metrics.maxTabWidth, availableTabsWidth / Math.max(1, windows.size())));
        DashboardWindowId active = controller.activeWindow(panelId);
        for (DashboardWindowId windowId : windows) {
            int width = Math.min(tabWidth, Math.max(1, right - x));
            if (width <= 0) {
                break;
            }
            assembly.add(new DashboardPrimitive("window-tab/" + windowId.id(), UiNodeType.WINDOW_TAB, layer, assembly.nextOrder(),
                    new UiRect(x, tabY, width, metrics.tabHeight), null,
                    panelProps(panelId, Map.of("title", windowId.titleKey(), "windowId", windowId.id(),
                            "active", windowId == active, "scale", metrics.uiScale))));
            x += width + metrics.gap;
        }
        if (controller.tabDropPreviewPanelId() == panelId) {
            assembly.add(new DashboardPrimitive("window-tab-drop-preview/" + panelId.id(), UiNodeType.PANEL, UiLayer.OVERLAY, assembly.nextOrder(),
                    new UiRect(panelRect.x() + metrics.innerInset, tabY,
                            Math.max(1, panelRect.width() - metrics.innerInset * 2 - metrics.headerButtonSize - metrics.smallGap),
                            metrics.tabHeight), null,
                    panelProps(panelId, Map.of("fill", 0x3034D399, "border", 0xCC34D399,
                            "role", "tab-drop-preview", "scale", metrics.uiScale))));
        }
        return tabY + metrics.tabHeight;
    }

    private DashboardWorkspaceLayout shiftedLayout(DashboardWorkspaceLayout layout, int dx, int dy) {
        if (dx == 0 && dy == 0) {
            return layout;
        }
        Map<DashboardDockSlotId, UiRect> shiftedSlots = new LinkedHashMap<>();
        for (Map.Entry<DashboardDockSlotId, UiRect> entry : layout.slotBounds().entrySet()) {
            shiftedSlots.put(entry.getKey(), shift(entry.getValue(), dx, dy));
        }
        List<DashboardDockResizeHandle> shiftedHandles = new ArrayList<>();
        for (DashboardDockResizeHandle handle : layout.resizeHandles()) {
            shiftedHandles.add(new DashboardDockResizeHandle(handle.id(), handle.slotId(), handle.edge(),
                    shift(handle.bounds(), dx, dy), handle.minRatio(), handle.maxRatio()));
        }
        return new DashboardWorkspaceLayout(layout.workspaceId(), shift(layout.shellBounds(), dx, dy),
                layout.slotSpecs(), shiftedSlots, shiftedHandles);
    }

    private UiRect shift(UiRect rect, int dx, int dy) {
        return new UiRect(rect.x() + dx, rect.y() + dy, rect.width(), rect.height());
    }

    private void appendPanelBackground(FrameAssembly assembly, DashboardPanelId panelId, UiRect panelRect, UiLayer layer,
                                       boolean floating, int fill, int border, Metrics metrics) {
        assembly.add(new DashboardPrimitive(panelId.id() + "-panel", UiNodeType.PANEL, layer, assembly.nextOrder(),
                panelRect, null,
                Map.of("fill", fill, "border", border, "role", "panel", "panelId", panelId.id(),
                        "floating", floating, "interactive", true, "scale", metrics.uiScale)));
    }

    private void appendModeToggle(FrameAssembly assembly, DashboardPanelId panelId, UiRect header, UiLayer layer,
                                  boolean floating, Metrics metrics) {
        UiRect buttonRect = modeToggleRect(header, metrics);
        assembly.add(new DashboardPrimitive("panel-mode-toggle/" + panelId.id(), UiNodeType.PANEL, layer, assembly.nextOrder(),
                buttonRect, null,
                Map.of("fill", 0x72202C39, "border", 0xAA4C627A, "role", "panel-mode-toggle", "interactive", true,
                        "panelId", panelId.id(), "floating", floating, "label", floating ? "D" : "F", "scale", metrics.uiScale)));
    }

    private UiRect modeToggleRect(UiRect header, Metrics metrics) {
        return new UiRect(
                header.right() - metrics.innerInset - metrics.headerButtonSize,
                header.y() + Math.max(4, (header.height() - metrics.headerButtonSize) / 2),
                metrics.headerButtonSize,
                metrics.headerButtonSize);
    }

    private int diagnosticsBadgeLaneWidth(int warningCount, int errorCount, int badgeGap, Metrics metrics) {
        int width = 0;
        if (errorCount > 0) {
            width += diagnosticsBadgeWidth("E", errorCount, metrics);
        }
        if (warningCount > 0) {
            if (width > 0) {
                width += badgeGap;
            }
            width += diagnosticsBadgeWidth("W", warningCount, metrics);
        }
        return width;
    }

    private int diagnosticsBadgeWidth(String prefix, int count, Metrics metrics) {
        int textWidth = (prefix.length() + 1 + String.valueOf(Math.max(0, count)).length()) * metrics.charWidth;
        return textWidth + 8;
    }

    private void appendResizeHandles(FrameAssembly assembly, DashboardPanelId panelId, UiRect panelRect, UiLayer layer, Metrics metrics) {
        int thickness = Math.max(4, Math.round(6 * metrics.uiScale));
        int corner = Math.max(10, Math.round(10 * metrics.uiScale));
        addResizeHandle(assembly, panelId, layer, panelRect, "N", new UiRect(panelRect.x() + corner, panelRect.y(), Math.max(1, panelRect.width() - corner * 2), thickness), metrics);
        addResizeHandle(assembly, panelId, layer, panelRect, "S", new UiRect(panelRect.x() + corner, panelRect.bottom() - thickness, Math.max(1, panelRect.width() - corner * 2), thickness), metrics);
        addResizeHandle(assembly, panelId, layer, panelRect, "W", new UiRect(panelRect.x(), panelRect.y() + corner, thickness, Math.max(1, panelRect.height() - corner * 2)), metrics);
        addResizeHandle(assembly, panelId, layer, panelRect, "E", new UiRect(panelRect.right() - thickness, panelRect.y() + corner, thickness, Math.max(1, panelRect.height() - corner * 2)), metrics);
        addResizeHandle(assembly, panelId, layer, panelRect, "NW", new UiRect(panelRect.x(), panelRect.y(), corner, corner), metrics);
        addResizeHandle(assembly, panelId, layer, panelRect, "NE", new UiRect(panelRect.right() - corner, panelRect.y(), corner, corner), metrics);
        addResizeHandle(assembly, panelId, layer, panelRect, "SW", new UiRect(panelRect.x(), panelRect.bottom() - corner, corner, corner), metrics);
        addResizeHandle(assembly, panelId, layer, panelRect, "SE", new UiRect(panelRect.right() - corner, panelRect.bottom() - corner, corner, corner), metrics);
    }

    private void addResizeHandle(FrameAssembly assembly, DashboardPanelId panelId, UiLayer layer, UiRect clipRect, String edge, UiRect bounds, Metrics metrics) {
        assembly.add(new DashboardPrimitive("panel-resize/" + panelId.id() + "/" + edge, UiNodeType.PANEL, layer, assembly.nextOrder(),
                bounds, clipRect,
                Map.of("fill", 0x00000000, "role", "panel-resize-handle", "interactive", true,
                        "panelId", panelId.id(), "edge", edge, "scale", metrics.uiScale)));
    }

    private void appendHomeSlotMetadata(FrameAssembly assembly, DashboardWorkspaceLayout layout, DashboardController controller, Metrics metrics) {
        boolean activeDockTarget = controller.dockPreviewPanelId() != null;
        for (Map.Entry<DashboardDockSlotId, UiRect> entry : layout.slotBounds().entrySet()) {
            DashboardDockSlotId slotId = entry.getKey();
            UiRect slotRect = entry.getValue();
            DashboardDockSlotSpec slotSpec = layout.slotSpec(slotId);
            assembly.add(new DashboardPrimitive("panel-home-slot/" + slotId.value(), UiNodeType.PANEL, UiLayer.PANELS, assembly.nextOrder(),
                slotRect, null,
                slotProps(slotSpec, Map.of("fill", 0, "border", activeDockTarget ? 0x4A3B4B5C : 0, "role", "panel-home-slot",
                        "slotId", slotId.value(), "interactive", true, "activeDockTarget", activeDockTarget, "scale", metrics.uiScale))));
        }
    }

    private void appendSlotResizeHandles(FrameAssembly assembly, DashboardWorkspaceLayout layout, Metrics metrics) {
        for (DashboardDockResizeHandle handle : layout.resizeHandles()) {
            assembly.add(new DashboardPrimitive(handle.id(), UiNodeType.PANEL, UiLayer.OVERLAY, assembly.nextOrder(),
                    handle.bounds(), null,
                    Map.of("fill", 0x00000000, "role", "slot-resize-handle", "interactive", true,
                            "slotId", handle.slotId().value(), "edge", handle.edge().name(),
                            "minRatio", handle.minRatio(), "maxRatio", handle.maxRatio(), "scale", metrics.uiScale)));
        }
    }

    private void appendSlotPreview(FrameAssembly assembly, DashboardWorkspaceLayout layout, DashboardController controller, Metrics metrics) {
        DashboardPanelId previewPanelId = controller.dockPreviewPanelId();
        DashboardDockSlotId previewSlotId = controller.dockPreviewSlotId();
        if (previewPanelId == null || previewSlotId == null) {
            return;
        }
        UiRect slotRect = layout.slotBounds(previewSlotId);
        if (slotRect == null) {
            return;
        }
        DashboardDockSlotSpec slotSpec = layout.slotSpec(previewSlotId);
        assembly.add(new DashboardPrimitive("panel-slot-preview/" + previewSlotId.value(), UiNodeType.PANEL, UiLayer.OVERLAY, assembly.nextOrder(),
                slotRect, null,
                slotProps(slotSpec, Map.of("fill", 0x2A34D399, "border", 0xCC34D399, "role", "panel-slot-preview",
                        "panelId", previewPanelId.id(), "slotId", previewSlotId.value(), "scale", metrics.uiScale))));
    }

    private UiRect slotBounds(DashboardWorkspaceLayout layout, DashboardController controller, DashboardPanelId panelId) {
        DashboardDockSlotId slotId = resolvedSlotId(controller, panelId);
        UiRect bounds = layout.slotBounds(slotId);
        if (bounds != null) {
            return bounds;
        }
        return layout.slotBounds().values().stream().findFirst().orElse(new UiRect(0, 0, 1, 1));
    }

    private DashboardDockSlotId resolvedSlotId(DashboardController controller, DashboardPanelId panelId) {
        DashboardDockSlotId slotId = controller.panelDockedSlotId(panelId);
        if (slotId == null) {
            slotId = controller.panelHomeSlotId(panelId);
        }
        if (slotId == null) {
            slotId = workspaceProfile.defaultHomeSlot(panelId);
        }
        return slotId;
    }

    private Map<String, Object> slotProps(@Nullable DashboardDockSlotSpec slotSpec, Map<String, Object> baseProps) {
        if (slotSpec == null) {
            return baseProps;
        }
        Map<String, Object> props = new LinkedHashMap<>(baseProps);
        props.put("slotRole", slotSpec.role().name());
        props.put("slotDisplayNameKey", slotSpec.displayNameKey());
        return props;
    }

    private UiRect resolveFloatingBounds(DashboardController controller, DashboardPanelId panelId, UiRect fallback) {
        UiRect stored = controller.panelFloatingBounds(panelId);
        return stored.width() > 0 && stored.height() > 0 ? stored : fallback;
    }

    private UiRect headerRect(UiRect panelRect, Metrics metrics) {
        return new UiRect(panelRect.x(), panelRect.y(), panelRect.width(), metrics.headerHeight);
    }

    private UiRect verticalTrackRect(UiRect viewport, UiRect panelRect, Metrics metrics) {
        return new UiRect(panelRect.right() - metrics.innerInset - metrics.scrollbarWidth,
                viewport.y(),
                metrics.scrollbarWidth,
                viewport.height());
    }

    private void appendVerticalScrollMetadata(FrameAssembly assembly, DashboardPanelId panelId, String area, UiRect viewportRect, int contentHeight,
                                              Metrics metrics, UiLayer layer) {
        assembly.add(new DashboardPrimitive("scroll-region/" + area, UiNodeType.PANEL, layer, assembly.nextOrder(),
                viewportRect, null,
                panelProps(panelId, Map.of("fill", 0, "role", "scroll-region", "scrollArea", area,
                        "viewportHeight", viewportRect.height(), "contentHeight", contentHeight, "scale", metrics.uiScale))));
    }

    private void appendHorizontalScrollMetadata(FrameAssembly assembly, DashboardPanelId panelId, String area, UiRect trackRect,
                                                int viewportWidth, int contentWidth,
                                                Metrics metrics, UiLayer layer) {
        assembly.add(new DashboardPrimitive("scroll-region/" + area, UiNodeType.PANEL, layer, assembly.nextOrder(),
                trackRect, null,
                panelProps(panelId, Map.of("fill", 0, "role", "scroll-region-x", "scrollArea", area,
                        "viewportWidth", viewportWidth, "contentWidth", contentWidth, "scale", metrics.uiScale))));
    }

    private void appendVerticalScrollbar(FrameAssembly assembly, DashboardPanelId panelId, String area, UiRect viewportRect,
                                         UiRect trackRect, UiRect clipRect,
                                         double scroll, int contentHeight, Metrics metrics, UiLayer layer) {
        if (contentHeight <= viewportRect.height()) {
            return;
        }
        int thumbHeight = Math.max(metrics.scrollbarThumbMinHeight,
                Math.round(trackRect.height() * (trackRect.height() / (float) contentHeight)));
        int maxScroll = Math.max(1, contentHeight - viewportRect.height());
        int travel = Math.max(1, trackRect.height() - thumbHeight);
        int thumbY = trackRect.y() + Math.round((float) (Math.min(scroll, maxScroll) / maxScroll) * travel);
        assembly.add(new DashboardPrimitive("scrollbar-track/" + area, UiNodeType.PANEL, layer, assembly.nextOrder(),
                trackRect, clipRect,
                panelProps(panelId, Map.of("fill", 0x2E273242, "role", "scrollbar-track", "interactive", true,
                        "scrollArea", area, "viewportHeight", viewportRect.height(), "contentHeight", contentHeight,
                        "thumbHeight", thumbHeight, "scale", metrics.uiScale))));
        assembly.add(new DashboardPrimitive("scrollbar-thumb/" + area, UiNodeType.PANEL, layer, assembly.nextOrder(),
                new UiRect(trackRect.x() + 1, thumbY, Math.max(3, trackRect.width() - 2), thumbHeight), clipRect,
                panelProps(panelId, Map.of("fill", 0xCC5E738B, "border", 0xFF8EA1B8, "role", "scrollbar-thumb",
                        "interactive", true, "scrollArea", area, "viewportHeight", viewportRect.height(),
                        "contentHeight", contentHeight, "thumbHeight", thumbHeight, "scale", metrics.uiScale))));
    }

    private void appendHorizontalScrollbar(FrameAssembly assembly, DashboardPanelId panelId, String area, UiRect trackRect, UiRect clipRect,
                                           double scroll, int viewportWidth, int contentWidth, Metrics metrics, UiLayer layer) {
        if (contentWidth <= viewportWidth) {
            return;
        }
        int thumbWidth = Math.max(metrics.horizontalScrollbarThumbMinWidth,
                Math.round(trackRect.width() * (trackRect.width() / (float) contentWidth)));
        int maxScroll = Math.max(1, contentWidth - viewportWidth);
        int travel = Math.max(1, trackRect.width() - thumbWidth);
        int thumbX = trackRect.x() + Math.round((float) (Math.min(scroll, maxScroll) / maxScroll) * travel);
        assembly.add(new DashboardPrimitive("scrollbar-track/" + area, UiNodeType.PANEL, layer, assembly.nextOrder(),
                trackRect, clipRect,
                panelProps(panelId, Map.of("fill", 0x2E273242, "role", "scrollbar-track-x", "interactive", true,
                        "scrollArea", area, "viewportWidth", viewportWidth, "contentWidth", contentWidth,
                        "thumbWidth", thumbWidth, "scale", metrics.uiScale))));
        assembly.add(new DashboardPrimitive("scrollbar-thumb/" + area, UiNodeType.PANEL, layer, assembly.nextOrder(),
                new UiRect(thumbX, trackRect.y() + 1, thumbWidth, Math.max(3, trackRect.height() - 2)), clipRect,
                panelProps(panelId, Map.of("fill", 0xCC5E738B, "border", 0xFF8EA1B8, "role", "scrollbar-thumb-x",
                        "interactive", true, "scrollArea", area, "viewportWidth", viewportWidth,
                        "contentWidth", contentWidth, "thumbWidth", thumbWidth, "scale", metrics.uiScale))));
    }

    private Map<String, Object> panelProps(DashboardPanelId panelId, Map<String, Object> baseProps) {
        Map<String, Object> props = new LinkedHashMap<>(baseProps);
        props.put("panelId", panelId.id());
        return props;
    }

    private int estimateDiagnosticsContentWidth(DashboardViewSnapshot snapshot, DashboardController controller, Metrics metrics) {
        int maxWidth = metrics.diagnosticBaseWidth;
        for (var line : snapshot.diagnostics()) {
            if (!controller.accepts(line.level())) {
                continue;
            }
            int width = metrics.diagnosticBaseWidth
                    + Math.max(0, line.moduleId().length()) * metrics.charWidth
                    + Math.max(0, line.message().length()) * metrics.charWidth
                    + (line.repeatCount() > 1 ? (4 + String.valueOf(line.repeatCount()).length()) * metrics.charWidth : 0);
            maxWidth = Math.max(maxWidth, width);
        }
        return maxWidth;
    }

    private DashboardMemoryRowLayout measureMemoryDomainRow(DashboardMemoryDomainMetric domainMetric, int width, Metrics metrics) {
        int padding = 10;
        int blockGap = 4;
        int barGap = 5;
        int barHeight = 6;
        int rightTextMaxWidth = Math.max(42, Math.round(width * 0.30f));
        int leftTextMaxWidth = Math.max(20, width - padding * 3 - rightTextMaxWidth);
        UiMeasuredTextBlock titleLayout = measureTextBlock(domainMetric.labelKey(), leftTextMaxWidth, metrics);
        UiMeasuredTextBlock reservedLayout = measureTextBlock(domainMetric.reservedText(), rightTextMaxWidth, metrics);

        String livePeak = "live " + domainMetric.liveText() + " / peak " + domainMetric.peakText();
        String tailText = "budget " + domainMetric.budgetText() + " | fragmentation " + domainMetric.fragmentationText();
        int tailMaxWidth = Math.max(36, Math.round(width * 0.42f));
        int detailWidth = Math.max(20, width - padding * 3 - tailMaxWidth);
        UiMeasuredTextBlock detailLayout = measureTextBlock(livePeak, detailWidth, metrics);
        UiMeasuredTextBlock tailLayout = measureTextBlock(tailText, tailMaxWidth, metrics);

        int titleBlockHeight = Math.max(titleLayout.height(), reservedLayout.height());
        int detailBlockHeight = Math.max(detailLayout.height(), tailLayout.height());
        int titleY = padding;
        int detailY = titleY + titleBlockHeight + blockGap;
        int usageLabelY = detailY + detailBlockHeight + barGap;
        int usageBarY = usageLabelY + metrics.lineHeight + barGap;
        int peakLabelY = usageBarY + barHeight + barGap * 2;
        int peakBarY = peakLabelY + metrics.lineHeight + barGap;
        int rowHeight = Math.max(96, peakBarY + barHeight + padding);
        return new DashboardMemoryRowLayout(
                padding,
                blockGap,
                barGap,
                barHeight,
                titleY,
                detailY,
                usageLabelY,
                usageBarY,
                peakLabelY,
                peakBarY,
                rowHeight,
                titleLayout,
                reservedLayout,
                detailLayout,
                tailLayout);
    }

    private int summaryMetricHeight(DashboardSummaryMetric metric, int width, int fallback, Metrics metrics) {
        UiMeasuredTextBlock titleLayout = measureTextBlock(metric.labelKey(), Math.max(24, width - metrics.innerInset * 2), metrics);
        UiMeasuredTextBlock valueLayout = measureTextBlock(
                metric.unitText().isEmpty() ? metric.valueText() : metric.valueText() + " " + metric.unitText(),
                Math.max(24, Math.round(width * 0.42f)), metrics);
        int lines = Math.max(1, Math.max(titleLayout.lineCount(), valueLayout.lineCount()));
        return Math.max(fallback, metrics.innerInset + lines * metrics.lineHeight + Math.max(0, lines - 1) * metrics.textLineGap + metrics.innerInset);
    }

    private int dynamicTextRowHeight(List<String> values, int width, int fallback, Metrics metrics) {
        int lines = 1;
        int lineWidth = Math.max(24, width - metrics.innerInset * 2);
        for (String value : values) {
            lines = Math.max(lines, measureTextBlock(value, lineWidth, metrics).lineCount());
        }
        return Math.max(fallback, metrics.innerInset + lines * metrics.lineHeight + Math.max(0, lines - 1) * metrics.textLineGap + metrics.innerInset);
    }

    private UiMeasuredTextBlock measureSingleLineText(String text, Metrics metrics) {
        String clipped = UiTextLayouts.clipWithEllipsis(textMetrics, text, Integer.MAX_VALUE);
        int width = textMetrics != null ? textMetrics.width(clipped) : clipped.length() * 6;
        return new UiMeasuredTextBlock(List.of(clipped), metrics.lineHeight, metrics.textLineGap, width, metrics.lineHeight);
    }

    private UiMeasuredTextBlock measureTextBlock(String text, int width, Metrics metrics) {
        return UiTextLayouts.measureWrapped(textMetrics, text, width, metrics.textLineGap);
    }

    private List<String> wrapText(String text, int width) {
        String safeText = text != null ? text : "";
        if (safeText.isBlank()) {
            return List.of("");
        }
        if (textMetrics == null) {
            int estimatedChars = Math.max(1, width / 6);
            List<String> lines = new ArrayList<>();
            for (String physicalLine : safeText.split("\\R", -1)) {
                if (physicalLine.isEmpty()) {
                    lines.add("");
                    continue;
                }
                int index = 0;
                while (index < physicalLine.length()) {
                    int end = Math.min(physicalLine.length(), index + estimatedChars);
                    lines.add(physicalLine.substring(index, end));
                    index = end;
                }
            }
            return lines;
        }
        List<String> result = new ArrayList<>();
        for (String physicalLine : safeText.split("\\R", -1)) {
            List<String> wrapped = textMetrics.wrap(UiText.literal(physicalLine), width);
            if (wrapped.isEmpty()) {
                result.add("");
            } else {
                result.addAll(wrapped);
            }
        }
        return result.isEmpty() ? List.of("") : result;
    }

    private static final class FrameAssembly {
        private final DashboardController controller;
        private final UiScaleContext scaleContext;
        private final UiRect screenBounds;
        private final java.util.List<UiPrimitive> primitives = new java.util.ArrayList<>();
        private final java.util.List<HitRegion> hitRegions = new java.util.ArrayList<>();
        private int nextOrder;

        private FrameAssembly(UiScaleContext scaleContext, DashboardController controller) {
            this.controller = controller;
            this.scaleContext = scaleContext != null ? scaleContext : UiScaleContext.of(1.0f, 1, 1);
            this.screenBounds = this.scaleContext.logicalViewport();
        }

        private int nextOrder() {
            return nextOrder++;
        }

        private void add(DashboardPrimitive primitive) {
            add((UiPrimitive) primitive);
        }

        private void add(UiPrimitive primitive) {
            UiPrimitive resolvedPrimitive = resolvePrimitive(primitive);
            if (!shouldAdd(resolvedPrimitive, screenBounds)) {
                return;
            }
            primitives.add(resolvedPrimitive);
            if (resolvedPrimitive instanceof DashboardPrimitive dashboardPrimitive && isInteractive(dashboardPrimitive)) {
                hitRegions.add(new HitRegion(dashboardPrimitive.id(), dashboardPrimitive.layer(), dashboardPrimitive.surface(), dashboardPrimitive.order(),
                        new RectHitShape(dashboardPrimitive.bounds()), dashboardPrimitive.clipRect(),
                        cursorHint(dashboardPrimitive), InputActionId.of(dashboardPrimitive.id()), Map.of("primitive", dashboardPrimitive)));
            }
        }

        private UiFrame build() {
            return new UiFrame(scaleContext, primitives, hitRegions);
        }

        private UiPrimitive resolvePrimitive(UiPrimitive primitive) {
            if (primitive instanceof DashboardPrimitive dashboardPrimitive) {
                return new DashboardPrimitive(
                        dashboardPrimitive.id(),
                        dashboardPrimitive.type(),
                        dashboardPrimitive.layer(),
                        dashboardPrimitive.order(),
                        dashboardPrimitive.bounds(),
                        dashboardPrimitive.clipRect(),
                        dashboardPrimitive.props(),
                        resolvePaintPass(dashboardPrimitive),
                        resolveSurface(dashboardPrimitive.surface(), dashboardPrimitive.props(), dashboardPrimitive.layer()));
            }
            if (primitive instanceof TexturePrimitive texturePrimitive) {
                return new TexturePrimitive(
                        texturePrimitive.layer(),
                        texturePrimitive.order(),
                        texturePrimitive.texture(),
                        texturePrimitive.rect(),
                        texturePrimitive.uv(),
                        texturePrimitive.tintArgb(),
                        texturePrimitive.clipRect(),
                        texturePrimitive.paintPass(),
                        resolveSurface(texturePrimitive.surface(), Map.of(), texturePrimitive.layer()));
            }
            return primitive;
        }

        private UiPaintPass resolvePaintPass(DashboardPrimitive primitive) {
            String role = stringProp(primitive.props(), "role");
            return switch (primitive.type()) {
                case POPUP_PANEL -> UiPaintPass.BACKGROUND;
                case POPUP_MENU_ITEM, TOPBAR_MENU_ITEM -> UiPaintPass.DECORATION;
                default -> "texture-preview-card".equals(role) ? UiPaintPass.DECORATION : UiPaintPass.DECORATION;
            };
        }

        private UiInteractionSurface resolveSurface(UiInteractionSurface explicitSurface, Map<String, Object> props, UiLayer layer) {
            if (explicitSurface != null) {
                if (explicitSurface.kind() == rogo.sketch.core.ui.frame.UiInteractionSurfaceKind.TOOLTIP) {
                    return explicitSurface.sortOrder() > 0 ? explicitSurface : UiInteractionSurface.tooltip(explicitSurface.ownerId(), 2_000);
                }
                if (explicitSurface.kind() == rogo.sketch.core.ui.frame.UiInteractionSurfaceKind.FLOATING_PANEL && !explicitSurface.ownerId().isBlank()) {
                    return UiInteractionSurface.floatingPanel(explicitSurface.ownerId(), floatingSurfaceOrder(DashboardPanelId.byId(explicitSurface.ownerId())));
                }
                if ((explicitSurface.kind() != rogo.sketch.core.ui.frame.UiInteractionSurfaceKind.CONTENT
                        || !explicitSurface.ownerId().isBlank()
                        || explicitSurface.sortOrder() != 0)
                        && explicitSurface.kind() != rogo.sketch.core.ui.frame.UiInteractionSurfaceKind.FLOATING_PANEL) {
                    return explicitSurface;
                }
            }
            String surfaceKind = stringProp(props, "surfaceKind");
            String surfaceOwnerId = stringProp(props, "surfaceOwnerId");
            if ("popup".equals(surfaceKind)) {
                return UiInteractionSurface.popup(surfaceOwnerId.isBlank() ? "popup" : surfaceOwnerId, popupSurfaceOrder(surfaceOwnerId, props));
            }
            if ("tooltip".equals(surfaceKind) || layer == UiLayer.TOOLTIP) {
                String ownerId = surfaceOwnerId.isBlank() ? "tooltip" : surfaceOwnerId;
                return UiInteractionSurface.tooltip(ownerId, 2_000);
            }
            DashboardPanelId panelId = DashboardPanelId.byId(stringProp(props, "panelId"));
            if (panelId != null && controller != null && controller.isFloating(panelId)) {
                return UiInteractionSurface.floatingPanel(panelId.id(), floatingSurfaceOrder(panelId));
            }
            return UiInteractionSurface.content(panelId != null ? panelId.id() : surfaceOwnerId);
        }

        private int popupSurfaceOrder(String surfaceOwnerId, Map<String, Object> props) {
            DashboardPanelId panelId = DashboardPanelId.byId(stringProp(props, "panelId"));
            int base = panelId != null ? floatingSurfaceOrder(panelId) : 0;
            if (surfaceOwnerId.startsWith("topbar:")) {
                base = Math.max(base, 40);
            }
            return 1_000 + base;
        }

        private int floatingSurfaceOrder(DashboardPanelId panelId) {
            if (panelId == null || controller == null) {
                return 100;
            }
            List<DashboardPanelId> floatingOrder = controller.floatingOrder();
            int index = floatingOrder.indexOf(panelId);
            return 100 + Math.max(0, index);
        }

        private String stringProp(Map<String, Object> props, String key) {
            Object value = props != null ? props.get(key) : null;
            return value != null ? String.valueOf(value) : "";
        }

        private static boolean isInteractive(DashboardPrimitive primitive) {
            Object interactive = primitive.props().get("interactive");
            if (interactive instanceof Boolean flag && flag) {
                return true;
            }
            return switch (primitive.type()) {
                case TAB_BUTTON, WINDOW_TAB, TOPBAR, TOPBAR_MENU_ITEM, POPUP_PANEL, POPUP_MENU_ITEM, LOG_LINE, LOG_COPY_BUTTON,
                     TREE_GROUP, TREE_CONTROL, MACRO_SECTION_HEADER, METRICS_LAYOUT_TOGGLE, CAPTURE_BUTTON, DIAGNOSTIC_FILTER -> true;
                default -> false;
            };
        }

        private static CursorHint cursorHint(DashboardPrimitive primitive) {
            String role = String.valueOf(primitive.props().getOrDefault("role", ""));
            if ("panel-resize-handle".equals(role) || "slot-resize-handle".equals(role)) {
                String edge = String.valueOf(primitive.props().getOrDefault("edge", ""));
                return edge.contains("E") || edge.contains("W") ? CursorHint.RESIZE_HORIZONTAL : CursorHint.RESIZE_VERTICAL;
            }
            if ("choice-dropdown-panel".equals(role) || "topbar-popup-panel".equals(role)) {
                return CursorHint.DEFAULT;
            }
            if ("panel-header".equals(role) && primitive.props().get("floating") instanceof Boolean floating && floating) {
                return CursorHint.GRAB;
            }
            if (primitive.type() == UiNodeType.TREE_CONTROL && primitive.props().get("editing") instanceof Boolean editing && editing) {
                return CursorHint.TEXT;
            }
            if (primitive.type() == UiNodeType.WINDOW_TAB && primitive.props().get("disabled") instanceof Boolean disabled && disabled) {
                return CursorHint.DEFAULT;
            }
            return CursorHint.POINTER;
        }

        private static boolean shouldAdd(UiPrimitive primitive, UiRect screenBounds) {
            UiRect bounds = primitiveBounds(primitive);
            if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
                return false;
            }
            if (!intersects(bounds, screenBounds)) {
                return false;
            }
            if (primitive.clipRect() == null) {
                return true;
            }
            return primitive.clipRect().width() > 0
                    && primitive.clipRect().height() > 0
                    && intersects(bounds, primitive.clipRect())
                    && intersects(primitive.clipRect(), screenBounds);
        }

        private static UiRect primitiveBounds(UiPrimitive primitive) {
            if (primitive instanceof DashboardPrimitive dashboardPrimitive) {
                return dashboardPrimitive.bounds();
            }
            if (primitive instanceof TexturePrimitive texturePrimitive) {
                return texturePrimitive.rect();
            }
            return null;
        }

        private static boolean intersects(UiRect a, UiRect b) {
            return a.right() > b.x() && b.right() > a.x() && a.bottom() > b.y() && b.bottom() > a.y();
        }
    }

    private static final class Metrics {
        private final float uiScale;
        private final float leftRatio;
        private final int autoMetricColumns;
        private final int shellInset;
        private final int columnGap;
        private final int panelGap;
        private final int innerInset;
        private final int contentPadding;
        private final int headerHeight;
        private final int tabHeight;
        private final int rowHeight;
        private final int groupHeight;
        private final int groupGap;
        private final int summaryRowHeight;
        private final int ratioRowHeight;
        private final int memoryDomainRowHeight;
        private final int chartHeight;
        private final int constantRowHeight;
        private final int logLineHeight;
        private final int filterWidth;
        private final int filterHeight;
        private final int topbarHeight;
        private final int topbarButtonWidth;
        private final int topbarMenuWidth;
        private final int topbarMenuRowHeight;
        private final int layoutToggleWidth;
        private final int layoutToggleHeight;
        private final int layoutToggleYOffset;
        private final int sectionGap;
        private final int gap;
        private final int smallGap;
        private final int lineHeight;
        private final int textLineGap;
        private final int indentStep;
        private final int minControlWidth;
        private final int minPanelWidth;
        private final int minPanelHeight;
        private final int minDiagnosticsHeight;
        private final int minDiagnosticsClipHeight;
        private final int minMetricColumnWidth;
        private final int minTabWidth;
        private final int maxTabWidth;
        private final int diagnosticsDockedHeight;
        private final int diagnosticBaseWidth;
        private final int charWidth;
        private final int scrollbarWidth;
        private final int scrollbarGutter;
        private final int scrollbarThumbMinHeight;
        private final int horizontalScrollbarHeight;
        private final int horizontalScrollbarThumbMinWidth;
        private final int headerButtonSize;
        private final int copyButtonWidth;
        private final int texturePreviewMinHeight;
        private final int texturePreviewMaxHeight;

        private Metrics(UiScaleContext scaleContext, int textLineHeight) {
            int screenWidth = scaleContext != null ? scaleContext.logicalViewport().width() : 1;
            int screenHeight = scaleContext != null ? scaleContext.logicalViewport().height() : 1;
            this.uiScale = 1.0f;
            this.leftRatio = DashboardUiDensityRules.defaultLeftRatio(screenWidth);
            this.autoMetricColumns = DashboardUiDensityRules.autoMetricColumns(screenWidth);
            this.shellInset = Math.max(6, Math.min(screenWidth, screenHeight) / 72);
            this.columnGap = 5;
            this.panelGap = 5;
            this.innerInset = 7;
            this.contentPadding = 6;
            this.headerHeight = 26;
            this.tabHeight = 21;
            this.rowHeight = 29;
            this.groupHeight = 20;
            this.groupGap = 4;
            this.summaryRowHeight = 34;
            this.ratioRowHeight = 66;
            this.memoryDomainRowHeight = 96;
            this.chartHeight = Math.max(108, screenHeight / 6);
            this.constantRowHeight = 34;
            this.logLineHeight = 20;
            this.filterWidth = Math.max(48, screenWidth / 28);
            this.filterHeight = 20;
            this.topbarHeight = 24;
            this.topbarButtonWidth = 82;
            this.topbarMenuWidth = 142;
            this.topbarMenuRowHeight = 22;
            this.layoutToggleWidth = 40;
            this.layoutToggleHeight = 18;
            this.layoutToggleYOffset = Math.max(6, (this.headerHeight - this.layoutToggleHeight) / 2);
            this.sectionGap = 8;
            this.gap = 6;
            this.smallGap = 4;
            this.lineHeight = Math.max(1, textLineHeight);
            this.textLineGap = 2;
            this.indentStep = 16;
            this.minControlWidth = 72;
            this.minPanelWidth = 126;
            this.minPanelHeight = 104;
            this.minDiagnosticsHeight = 72;
            this.minDiagnosticsClipHeight = 34;
            this.minMetricColumnWidth = 84;
            this.minTabWidth = 34;
            this.maxTabWidth = 128;
            this.diagnosticsDockedHeight = Math.max(92, Math.min(screenHeight / 3, 180));
            this.charWidth = Math.max(6, Math.max(1, (this.lineHeight * 2) / 3));
            this.diagnosticBaseWidth = 150;
            this.scrollbarWidth = 8;
            this.scrollbarGutter = 14;
            this.scrollbarThumbMinHeight = 22;
            this.horizontalScrollbarHeight = 8;
            this.horizontalScrollbarThumbMinWidth = 24;
            this.headerButtonSize = 16;
            this.copyButtonWidth = 52;
            this.texturePreviewMinHeight = 80;
            this.texturePreviewMaxHeight = 220;
        }

    }
}
