package rogo.sketch.core.dashboard;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.debugger.DashboardController;
import rogo.sketch.core.debugger.DashboardDockResizeEdge;
import rogo.sketch.core.debugger.DashboardDockResizeHandle;
import rogo.sketch.core.debugger.DashboardDockSlotId;
import rogo.sketch.core.debugger.DashboardPanelId;
import rogo.sketch.core.debugger.DashboardTab;
import rogo.sketch.core.debugger.DashboardTreeNode;
import rogo.sketch.core.debugger.DashboardDockSlotSpec;
import rogo.sketch.core.debugger.DashboardWorkspaceLayout;
import rogo.sketch.core.debugger.DashboardWorkspaceProfile;
import rogo.sketch.core.debugger.DashboardWorkspaceProfiles;
import rogo.sketch.core.debugger.ui.UiNodeType;
import rogo.sketch.core.pipeline.module.diagnostic.DiagnosticLevel;
import rogo.sketch.core.ui.frame.UiFrame;
import rogo.sketch.core.ui.frame.UiLayer;
import rogo.sketch.core.ui.frame.UiPrimitive;
import rogo.sketch.core.ui.geometry.UiRect;
import rogo.sketch.core.ui.input.CursorHint;
import rogo.sketch.core.ui.input.HitRegion;
import rogo.sketch.core.ui.input.InputActionId;
import rogo.sketch.core.ui.input.RectHitShape;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DashboardViewSceneBuilder {
    private final DashboardWorkspaceProfile workspaceProfile;

    public DashboardViewSceneBuilder() {
        this(DashboardWorkspaceProfiles.dashboardDefault());
    }

    public DashboardViewSceneBuilder(DashboardWorkspaceProfile workspaceProfile) {
        this.workspaceProfile = workspaceProfile != null ? workspaceProfile : DashboardWorkspaceProfiles.dashboardDefault();
    }

    public UiFrame build(DashboardViewSnapshot snapshot, DashboardController controller, int screenWidth, int screenHeight, float uiScale) {
        return build(snapshot, controller, screenWidth, screenHeight, uiScale, null, null);
    }

    public UiFrame build(DashboardViewSnapshot snapshot, DashboardController controller, int screenWidth, int screenHeight, float uiScale,
                         @Nullable String editingNumberControlId, @Nullable String editingDraftValue) {
        Metrics metrics = new Metrics(uiScale, screenWidth, screenHeight);
        FrameAssembly assembly = new FrameAssembly(new UiRect(0, 0, screenWidth, screenHeight));
        assembly.add(new DashboardPrimitive("background", UiNodeType.PANEL, UiLayer.BACKGROUND, assembly.nextOrder(),
                new UiRect(0, 0, screenWidth, screenHeight), null,
                Map.of("fill", 0x64101922, "role", "backdrop")));

        DashboardWorkspaceLayout layout = workspaceProfile.layout(screenWidth, screenHeight, uiScale, controller);
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

        UiRect tabRow = new UiRect(panelRect.x() + metrics.innerInset, header.bottom() + metrics.sectionGap,
                panelRect.width() - metrics.innerInset * 2, metrics.tabHeight);
        int modeToggleReserve = metrics.headerButtonSize + metrics.smallGap;
        int availableTabWidth = Math.max(48, tabRow.width() - modeToggleReserve - metrics.gap);
        int tabWidth = Math.max(24, availableTabWidth / 2);
        assembly.add(new DashboardPrimitive("tab-settings", UiNodeType.TAB_BUTTON, contentLayer, assembly.nextOrder(),
                new UiRect(tabRow.x(), tabRow.y(), tabWidth, tabRow.height()), null,
                panelProps(DashboardPanelId.SETTINGS, Map.of("title", "debug.dashboard.tab.settings",
                        "active", controller.activeTab() == DashboardTab.MOD_SETTINGS, "scale", metrics.uiScale))));
        assembly.add(new DashboardPrimitive("tab-macros", UiNodeType.TAB_BUTTON, contentLayer, assembly.nextOrder(),
                new UiRect(tabRow.x() + tabWidth + metrics.gap, tabRow.y(), tabWidth, tabRow.height()), null,
                panelProps(DashboardPanelId.SETTINGS, Map.of("title", "debug.dashboard.tab.macros",
                        "active", controller.activeTab() == DashboardTab.SHADER_MACROS, "scale", metrics.uiScale))));

        UiRect viewport = new UiRect(panelRect.x() + metrics.innerInset,
                tabRow.bottom() + metrics.sectionGap,
                Math.max(1, panelRect.width() - metrics.innerInset * 2 - metrics.scrollbarGutter),
                Math.max(1, panelRect.bottom() - (tabRow.bottom() + metrics.sectionGap) - metrics.innerInset));
        UiRect track = verticalTrackRect(viewport, panelRect, metrics);
        List<DashboardTreeNode> roots = controller.activeTab() == DashboardTab.MOD_SETTINGS ? snapshot.settingRoots() : snapshot.macroRoots();
        int y = viewport.y() - (int) Math.round(controller.settingsScroll());
        for (DashboardTreeNode root : roots) {
            y = appendTree(assembly, root, 0, viewport, y, controller, editingNumberControlId, editingDraftValue, metrics, contentLayer);
        }
        int contentHeight = Math.max(viewport.height(), y + (int) Math.round(controller.settingsScroll()) - viewport.y());
        appendVerticalScrollMetadata(assembly, DashboardPanelId.SETTINGS, "settings", viewport, contentHeight, metrics, contentLayer);
        appendVerticalScrollbar(assembly, DashboardPanelId.SETTINGS, "settings", viewport, track, panelRect,
                controller.settingsScroll(), contentHeight, metrics, contentLayer);
    }

    private int appendTree(FrameAssembly assembly, DashboardTreeNode node, int depth, UiRect viewport, int startY, DashboardController controller,
                           @Nullable String editingNumberControlId, @Nullable String editingDraftValue, Metrics metrics, UiLayer layer) {
        int indent = depth * metrics.indentStep;
        int x = viewport.x() + metrics.contentPadding + indent;
        int width = Math.max(metrics.minControlWidth, viewport.width() - metrics.contentPadding * 2 - indent);
        if (node.group()) {
            assembly.add(new DashboardPrimitive(node.id(), UiNodeType.TREE_GROUP, layer, assembly.nextOrder(),
                    new UiRect(x, startY, width, metrics.groupHeight), viewport,
                    panelProps(DashboardPanelId.SETTINGS, Map.of("title", node.displayKey(),
                            "summary", Objects.toString(node.summaryKey(), ""), "expanded", controller.isExpanded(node.id()),
                            "scale", metrics.uiScale))));
            startY += metrics.groupHeight + metrics.smallGap;
            if (controller.isExpanded(node.id())) {
                for (DashboardTreeNode child : node.children()) {
                    startY = appendTree(assembly, child, depth + 1, viewport, startY, controller, editingNumberControlId, editingDraftValue, metrics, layer);
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
        props.put("panelId", DashboardPanelId.SETTINGS.id());
        assembly.add(new DashboardPrimitive(node.id(), UiNodeType.TREE_CONTROL, layer, assembly.nextOrder(),
                new UiRect(x, startY, width, metrics.rowHeight), viewport, props));
        startY += metrics.rowHeight + metrics.smallGap;
        if (node.expandable() && controller.isExpanded(node.id())) {
            for (DashboardTreeNode child : node.children()) {
                startY = appendTree(assembly, child, depth + 1, viewport, startY, controller, editingNumberControlId, editingDraftValue, metrics, layer);
            }
            startY += metrics.groupGap;
        }
        return startY;
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

        int contentWidth = Math.max(metrics.minMetricColumnWidth, panelRect.width() - metrics.innerInset * 2 - metrics.scrollbarGutter);
        int columns = resolveMetricColumns(controller, contentWidth, metrics);
        assembly.add(new DashboardPrimitive("metrics-layout-toggle", UiNodeType.METRICS_LAYOUT_TOGGLE, contentLayer, assembly.nextOrder(),
                new UiRect(panelRect.right() - metrics.innerInset - metrics.headerButtonSize - metrics.smallGap - metrics.layoutToggleWidth,
                        panelRect.y() + metrics.layoutToggleYOffset,
                        metrics.layoutToggleWidth,
                        metrics.layoutToggleHeight), null,
                panelProps(DashboardPanelId.METRICS, Map.of("layoutMode", controller.metricsLayoutMode().name(),
                        "columns", columns, "scale", metrics.uiScale))));

        UiRect viewport = new UiRect(panelRect.x() + metrics.innerInset,
                panelRect.y() + metrics.headerHeight + metrics.sectionGap,
                Math.max(1, panelRect.width() - metrics.innerInset * 2 - metrics.scrollbarGutter),
                Math.max(1, panelRect.bottom() - (panelRect.y() + metrics.headerHeight + metrics.sectionGap) - metrics.innerInset));
        UiRect track = verticalTrackRect(viewport, panelRect, metrics);

        int y = viewport.y() - (int) Math.round(controller.metricsScroll());
        int columnGap = metrics.gap;
        int columnWidth = columns <= 1 ? viewport.width() : Math.max(metrics.minMetricColumnWidth, (viewport.width() - columnGap * (columns - 1)) / columns);
        y = appendSummaryMetricRows(
                assembly,
                DashboardPanelId.METRICS,
                snapshot.summaryMetrics(),
                viewport,
                y,
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
                    panelProps(DashboardPanelId.METRICS, Map.of("title", ratioMetric.labelKey(), "mode", "ratio-row",
                            "hidden", ratioMetric.hiddenCount(), "visible", ratioMetric.visibleCount(),
                            "total", ratioMetric.totalCount(), "ratio", ratioMetric.hiddenRatio(),
                            "accent", ratioMetric.accentColor(), "detail", ratioMetric.detailKey(), "scale", metrics.uiScale))));
            y += metrics.ratioRowHeight + metrics.sectionGap;
        }

        y = appendMemorySection(
                assembly,
                snapshot.memorySection(),
                viewport,
                y,
                controller,
                columns,
                columnGap,
                metrics,
                contentLayer);

        assembly.add(new DashboardPrimitive("frame-chart", UiNodeType.BAR_CHART, contentLayer, assembly.nextOrder(),
                new UiRect(viewport.x(), y, viewport.width(), metrics.chartHeight), viewport,
                panelProps(DashboardPanelId.METRICS, Map.of("bars", snapshot.frameTimeHistory(),
                        "title", "debug.dashboard.frame_history", "threshold", 33.0D,
                        "scale", metrics.uiScale, "mode", "frame-chart"))));
        y += metrics.chartHeight + metrics.sectionGap;

        assembly.add(new DashboardPrimitive("macro-constants-header", UiNodeType.MACRO_SECTION_HEADER, contentLayer, assembly.nextOrder(),
                new UiRect(viewport.x(), y, viewport.width(), metrics.groupHeight), viewport,
                panelProps(DashboardPanelId.METRICS, Map.of("title", "debug.dashboard.macro_constants",
                        "expanded", controller.macroConstantsExpanded(), "scale", metrics.uiScale,
                        "sectionId", "macro-constants"))));
        y += metrics.groupHeight + metrics.smallGap;

        if (controller.macroConstantsExpanded()) {
            for (DashboardMacroConstantView constant : snapshot.macroConstants()) {
                assembly.add(new DashboardPrimitive("macro-constant/" + constant.name() + "/" + constant.sourceText(),
                        UiNodeType.MACRO_CONSTANT_ROW, contentLayer, assembly.nextOrder(),
                        new UiRect(viewport.x(), y, viewport.width(), metrics.constantRowHeight), viewport,
                        panelProps(DashboardPanelId.METRICS, Map.of("name", constant.name(), "value", constant.value(),
                                "flag", constant.flag(), "source", constant.sourceText(), "type", constant.typeText(),
                                "detail", constant.detailText(), "scale", metrics.uiScale, "mode", "macro-constant-row"))));
                y += metrics.constantRowHeight + metrics.smallGap;
            }
        }

        int contentHeight = Math.max(viewport.height(), y + (int) Math.round(controller.metricsScroll()) - viewport.y());
        appendVerticalScrollMetadata(assembly, DashboardPanelId.METRICS, "metrics", viewport, contentHeight, metrics, contentLayer);
        appendVerticalScrollbar(assembly, DashboardPanelId.METRICS, "metrics", viewport, track, panelRect,
                controller.metricsScroll(), contentHeight, metrics, contentLayer);
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
            for (int column = 0; column < columns && index < metricsSnapshot.size(); column++, index++) {
                DashboardSummaryMetric metric = metricsSnapshot.get(index);
                int itemX = viewport.x() + column * (columnWidth + columnGap);
                assembly.add(new DashboardPrimitive(idPrefix + metric.id(), UiNodeType.METRIC_CARD, contentLayer, assembly.nextOrder(),
                        new UiRect(itemX, rowStartY, columnWidth, rowHeight), viewport,
                        panelProps(panelId, Map.of(
                                "title", metric.labelKey(),
                                "value", metric.valueText(),
                                "unit", metric.unitText(),
                                "accent", metric.accentColor(),
                                "detail", metric.detailKey(),
                                "scale", metrics.uiScale,
                                "mode", "summary-row"))));
            }
            y += rowHeight + metrics.smallGap;
        }
        return y;
    }

    private int appendMemorySection(
            FrameAssembly assembly,
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
                panelProps(DashboardPanelId.METRICS, Map.of(
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
                DashboardPanelId.METRICS,
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
            domainProps.put("scale", metrics.uiScale);
            domainProps.put("mode", "memory-domain-row");
            assembly.add(new DashboardPrimitive(domainMetric.id(), UiNodeType.METRIC_CARD, contentLayer, assembly.nextOrder(),
                    new UiRect(viewport.x(), y, viewport.width(), metrics.ratioRowHeight), viewport,
                    panelProps(DashboardPanelId.METRICS, domainProps)));
            y += metrics.ratioRowHeight + metrics.sectionGap;
        }

        assembly.add(new DashboardPrimitive("memory-chart", UiNodeType.BAR_CHART, contentLayer, assembly.nextOrder(),
                new UiRect(viewport.x(), y, viewport.width(), metrics.chartHeight), viewport,
                panelProps(DashboardPanelId.METRICS, Map.of(
                        "bars", memorySection.timelineValues(),
                        "title", memorySection.timelineTitleKey(),
                        "threshold", memorySection.timelineThreshold(),
                        "scale", metrics.uiScale,
                        "mode", "memory-chart"))));
        return y + metrics.chartHeight + metrics.sectionGap;
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

        int filtersY = header.bottom() + metrics.smallGap;
        int filterX = panelRect.x() + metrics.innerInset;
        for (DiagnosticLevel level : DiagnosticLevel.values()) {
            assembly.add(new DashboardPrimitive("diag-filter/" + level.name(), UiNodeType.DIAGNOSTIC_FILTER, contentLayer, assembly.nextOrder(),
                    new UiRect(filterX, filtersY, metrics.filterWidth, metrics.filterHeight), null,
                    panelProps(DashboardPanelId.DIAGNOSTICS, Map.of("level", level.name(),
                            "active", controller.diagnosticFilters().contains(level), "scale", metrics.uiScale))));
            filterX += metrics.filterWidth + metrics.smallGap;
        }

        int viewportWidth = Math.max(metrics.minDiagnosticsClipHeight, panelRect.width() - metrics.innerInset * 2 - metrics.scrollbarGutter);
        int contentWidth = Math.max(viewportWidth, estimateDiagnosticsContentWidth(snapshot, controller, metrics));
        boolean needsHorizontalScrollbar = contentWidth > viewportWidth;
        int viewportY = filtersY + metrics.filterHeight + metrics.sectionGap;
        int viewportHeight = panelRect.bottom() - viewportY - metrics.innerInset;
        if (needsHorizontalScrollbar) {
            viewportHeight -= metrics.horizontalScrollbarHeight + metrics.smallGap;
        }
        viewportHeight = Math.max(metrics.minDiagnosticsClipHeight, viewportHeight);

        UiRect viewport = new UiRect(panelRect.x() + metrics.innerInset, viewportY, viewportWidth, viewportHeight);
        UiRect verticalTrack = verticalTrackRect(viewport, panelRect, metrics);
        int y = viewport.y() - (int) Math.round(controller.diagnosticsScroll());
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
            assembly.add(new DashboardPrimitive("log/" + visibleCount, UiNodeType.LOG_LINE, contentLayer, assembly.nextOrder(),
                    new UiRect(viewport.x() - (int) Math.round(controller.diagnosticsHorizontalScroll()), y, contentWidth, metrics.logLineHeight),
                    viewport,
                    panelProps(DashboardPanelId.DIAGNOSTICS, Map.of("time", line.timeText(), "level", line.level().name(),
                            "module", line.moduleId(), "message", line.message(), "repeat", line.repeatCount(),
                            "detail", Objects.toString(line.stackPreview(), ""),
                            "scale", metrics.uiScale))));
            y += metrics.logLineHeight;
            if (visibleCount < totalVisibleCount) {
                y += metrics.smallGap;
            }
        }
        int contentHeight = visibleCount == 0
                ? viewport.height()
                : Math.max(viewport.height(), y + (int) Math.round(controller.diagnosticsScroll()) - viewport.y());
        appendVerticalScrollMetadata(assembly, DashboardPanelId.DIAGNOSTICS, "diagnostics", viewport, contentHeight, metrics, contentLayer);
        appendVerticalScrollbar(assembly, DashboardPanelId.DIAGNOSTICS, "diagnostics", viewport, verticalTrack, panelRect,
                controller.diagnosticsScroll(), contentHeight, metrics, contentLayer);

        if (needsHorizontalScrollbar) {
            UiRect horizontalTrack = new UiRect(viewport.x(), viewport.bottom() + metrics.smallGap, viewport.width(), metrics.horizontalScrollbarHeight);
            appendHorizontalScrollMetadata(assembly, DashboardPanelId.DIAGNOSTICS, "diagnostics-x", horizontalTrack,
                    viewport.width(), contentWidth, metrics, contentLayer);
            appendHorizontalScrollbar(assembly, DashboardPanelId.DIAGNOSTICS, "diagnostics-x", horizontalTrack, panelRect,
                    controller.diagnosticsHorizontalScroll(), viewport.width(), contentWidth, metrics, contentLayer);
        }
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

    private static final class FrameAssembly {
        private final UiRect screenBounds;
        private final java.util.List<UiPrimitive> primitives = new java.util.ArrayList<>();
        private final java.util.List<HitRegion> hitRegions = new java.util.ArrayList<>();
        private int nextOrder;

        private FrameAssembly(UiRect screenBounds) {
            this.screenBounds = screenBounds;
        }

        private int nextOrder() {
            return nextOrder++;
        }

        private void add(DashboardPrimitive primitive) {
            if (!shouldAdd(primitive, screenBounds)) {
                return;
            }
            primitives.add(primitive);
            if (isInteractive(primitive)) {
                hitRegions.add(new HitRegion(primitive.id(), primitive.layer(), primitive.order(),
                        new RectHitShape(primitive.bounds()), primitive.clipRect(),
                        cursorHint(primitive), InputActionId.of(primitive.id()), Map.of("primitive", primitive)));
            }
        }

        private UiFrame build() {
            return new UiFrame(primitives, hitRegions);
        }

        private static boolean isInteractive(DashboardPrimitive primitive) {
            Object interactive = primitive.props().get("interactive");
            if (interactive instanceof Boolean flag && flag) {
                return true;
            }
            return switch (primitive.type()) {
                case TAB_BUTTON, TREE_GROUP, TREE_CONTROL, MACRO_SECTION_HEADER, METRICS_LAYOUT_TOGGLE, DIAGNOSTIC_FILTER -> true;
                default -> false;
            };
        }

        private static CursorHint cursorHint(DashboardPrimitive primitive) {
            String role = String.valueOf(primitive.props().getOrDefault("role", ""));
            if ("panel-resize-handle".equals(role) || "slot-resize-handle".equals(role)) {
                String edge = String.valueOf(primitive.props().getOrDefault("edge", ""));
                return edge.contains("E") || edge.contains("W") ? CursorHint.RESIZE_HORIZONTAL : CursorHint.RESIZE_VERTICAL;
            }
            if ("panel-header".equals(role) && primitive.props().get("floating") instanceof Boolean floating && floating) {
                return CursorHint.GRAB;
            }
            if (primitive.type() == UiNodeType.TREE_CONTROL && primitive.props().get("editing") instanceof Boolean editing && editing) {
                return CursorHint.TEXT;
            }
            return CursorHint.POINTER;
        }

        private static boolean shouldAdd(DashboardPrimitive primitive, UiRect screenBounds) {
            if (primitive.bounds().width() <= 0 || primitive.bounds().height() <= 0) {
                return false;
            }
            if (!intersects(primitive.bounds(), screenBounds)) {
                return false;
            }
            if (primitive.clipRect() == null) {
                return true;
            }
            return primitive.clipRect().width() > 0
                    && primitive.clipRect().height() > 0
                    && intersects(primitive.bounds(), primitive.clipRect())
                    && intersects(primitive.clipRect(), screenBounds);
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
        private final int chartHeight;
        private final int constantRowHeight;
        private final int logLineHeight;
        private final int filterWidth;
        private final int filterHeight;
        private final int layoutToggleWidth;
        private final int layoutToggleHeight;
        private final int layoutToggleYOffset;
        private final int sectionGap;
        private final int gap;
        private final int smallGap;
        private final int indentStep;
        private final int minControlWidth;
        private final int minPanelWidth;
        private final int minPanelHeight;
        private final int minDiagnosticsHeight;
        private final int minDiagnosticsClipHeight;
        private final int minMetricColumnWidth;
        private final int diagnosticsDockedHeight;
        private final int diagnosticBaseWidth;
        private final int charWidth;
        private final int scrollbarWidth;
        private final int scrollbarGutter;
        private final int scrollbarThumbMinHeight;
        private final int horizontalScrollbarHeight;
        private final int horizontalScrollbarThumbMinWidth;
        private final int headerButtonSize;

        private Metrics(float inputScale, int screenWidth, int screenHeight) {
            this.uiScale = Math.max(0.70f, inputScale * 0.90f);
            this.leftRatio = this.uiScale <= 0.80f ? 0.37f : this.uiScale <= 0.94f ? 0.41f : this.uiScale <= 1.08f ? 0.45f : 0.49f;
            this.autoMetricColumns = this.uiScale <= 0.86f ? 3 : this.uiScale <= 1.08f ? 2 : 1;
            this.shellInset = Math.max(scaled(14), Math.min(screenWidth, screenHeight) / 40);
            this.columnGap = scaled(10);
            this.panelGap = scaled(10);
            this.innerInset = scaled(10);
            this.contentPadding = scaled(8);
            this.headerHeight = scaled(30);
            this.tabHeight = scaled(24);
            this.rowHeight = scaled(31);
            this.groupHeight = scaled(22);
            this.groupGap = scaled(4);
            this.summaryRowHeight = scaled(34);
            this.ratioRowHeight = scaled(66);
            this.chartHeight = Math.max(scaled(108), screenHeight / 6);
            this.constantRowHeight = scaled(34);
            this.logLineHeight = scaled(20);
            this.filterWidth = Math.max(scaled(48), screenWidth / 28);
            this.filterHeight = scaled(20);
            this.layoutToggleWidth = scaled(40);
            this.layoutToggleHeight = scaled(18);
            this.layoutToggleYOffset = Math.max(scaled(6), (this.headerHeight - this.layoutToggleHeight) / 2);
            this.sectionGap = scaled(8);
            this.gap = scaled(6);
            this.smallGap = scaled(4);
            this.indentStep = scaled(16);
            this.minControlWidth = scaled(96);
            this.minPanelWidth = scaled(180);
            this.minPanelHeight = scaled(160);
            this.minDiagnosticsHeight = scaled(140);
            this.minDiagnosticsClipHeight = scaled(48);
            this.minMetricColumnWidth = scaled(110);
            this.diagnosticsDockedHeight = Math.max(scaled(170), Math.min(screenHeight / 3, scaled(220)));
            this.charWidth = scaled(6);
            this.diagnosticBaseWidth = scaled(180);
            this.scrollbarWidth = scaled(8);
            this.scrollbarGutter = scaled(14);
            this.scrollbarThumbMinHeight = scaled(22);
            this.horizontalScrollbarHeight = scaled(8);
            this.horizontalScrollbarThumbMinWidth = scaled(24);
            this.headerButtonSize = scaled(16);
        }

        private int scaled(int base) {
            return Math.max(1, Math.round(base * uiScale));
        }
    }
}
