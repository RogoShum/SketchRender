
package rogo.sketch.core.dashboard;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.debugger.DashboardController;
import rogo.sketch.core.debugger.DashboardTab;
import rogo.sketch.core.debugger.DiagnosticsPanelMode;
import rogo.sketch.core.debugger.DashboardTreeNode;
import rogo.sketch.core.debugger.ui.UiNode;
import rogo.sketch.core.debugger.ui.UiNodeType;
import rogo.sketch.core.debugger.ui.UiPass;
import rogo.sketch.core.debugger.ui.UiRect;
import rogo.sketch.core.debugger.ui.UiScene;
import rogo.sketch.core.pipeline.module.diagnostic.DiagnosticLevel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DashboardViewSceneBuilder {
    public UiScene build(DashboardViewSnapshot snapshot, DashboardController controller, int screenWidth, int screenHeight, float uiScale) {
        return build(snapshot, controller, screenWidth, screenHeight, uiScale, null, null);
    }

    public UiScene build(DashboardViewSnapshot snapshot, DashboardController controller, int screenWidth, int screenHeight, float uiScale,
                         @Nullable String editingNumberControlId, @Nullable String editingDraftValue) {
        Metrics metrics = new Metrics(uiScale, screenWidth, screenHeight);
        UiScene scene = new UiScene();
        scene.add(new UiNode("background", UiNodeType.PANEL, UiPass.BACKGROUND, new UiRect(0, 0, screenWidth, screenHeight), null,
                Map.of("fill", 0x64101922, "role", "backdrop")));

        UiRect shell = new UiRect(metrics.shellInset, metrics.shellInset,
                Math.max(1, screenWidth - metrics.shellInset * 2),
                Math.max(1, screenHeight - metrics.shellInset * 2));

        boolean collapsedDiagnostics = controller.diagnosticsPanelMode() == DiagnosticsPanelMode.COLLAPSED;
        int contentHeight = collapsedDiagnostics
                ? Math.max(metrics.minPanelHeight, shell.height() - metrics.collapsedHeight - metrics.panelGap)
                : shell.height();
        UiRect contentShell = new UiRect(shell.x(), shell.y(), shell.width(), contentHeight);
        UiRect collapsedDiagnosticsRect = collapsedDiagnostics
                ? new UiRect(shell.x(), contentShell.bottom() + metrics.panelGap, shell.width(), Math.max(metrics.collapsedHeight, shell.bottom() - (contentShell.bottom() + metrics.panelGap)))
                : null;

        int leftWidth = Math.round(contentShell.width() * metrics.leftRatio);
        int rightWidth = Math.max(metrics.minPanelWidth, contentShell.width() - leftWidth - metrics.columnGap);
        leftWidth = Math.max(metrics.minPanelWidth, contentShell.width() - rightWidth - metrics.columnGap);

        UiRect leftPanel = new UiRect(contentShell.x(), contentShell.y(), leftWidth, contentShell.height());
        UiRect rightPanel = new UiRect(leftPanel.right() + metrics.columnGap, contentShell.y(), rightWidth, contentShell.height());

        scene.add(new UiNode("left-panel", UiNodeType.PANEL, UiPass.PANELS, leftPanel, null,
                Map.of("fill", 0xB6151C28, "border", 0xA935465E, "role", "panel")));
        scene.add(new UiNode("right-panel", UiNodeType.PANEL, UiPass.PANELS, rightPanel, null,
                Map.of("fill", 0xB1131924, "border", 0xA92F3F55, "role", "panel")));

        buildSettings(scene, snapshot, controller, leftPanel, editingNumberControlId, editingDraftValue, metrics);
        buildRightColumn(scene, snapshot, controller, shell, rightPanel, collapsedDiagnosticsRect, metrics);
        return scene;
    }

    private void buildSettings(UiScene scene, DashboardViewSnapshot snapshot, DashboardController controller, UiRect leftPanel,
                               @Nullable String editingNumberControlId, @Nullable String editingDraftValue, Metrics metrics) {
        UiRect header = new UiRect(leftPanel.x(), leftPanel.y(), leftPanel.width(), metrics.headerHeight);
        scene.add(new UiNode("settings-header", UiNodeType.HEADER, UiPass.PANELS, header, null,
                Map.of("title", "debug.dashboard.configuration", "fill", 0xD61A2330, "border", 0x7A37485F, "role", "header", "scale", metrics.uiScale)));

        UiRect tabRow = new UiRect(leftPanel.x() + metrics.innerInset, header.bottom() + metrics.sectionGap,
                leftPanel.width() - metrics.innerInset * 2, metrics.tabHeight);
        int tabWidth = (tabRow.width() - metrics.gap) / 2;
        scene.add(new UiNode("tab-settings", UiNodeType.TAB_BUTTON, UiPass.CONTROLS,
                new UiRect(tabRow.x(), tabRow.y(), tabWidth, tabRow.height()), null,
                Map.of("title", "debug.dashboard.tab.settings", "active", controller.activeTab() == DashboardTab.MOD_SETTINGS, "scale", metrics.uiScale)));
        scene.add(new UiNode("tab-macros", UiNodeType.TAB_BUTTON, UiPass.CONTROLS,
                new UiRect(tabRow.x() + tabWidth + metrics.gap, tabRow.y(), tabWidth, tabRow.height()), null,
                Map.of("title", "debug.dashboard.tab.macros", "active", controller.activeTab() == DashboardTab.SHADER_MACROS, "scale", metrics.uiScale)));

        UiRect contentClip = new UiRect(leftPanel.x() + metrics.innerInset, tabRow.bottom() + metrics.sectionGap,
                leftPanel.width() - metrics.innerInset * 2,
                leftPanel.bottom() - (tabRow.bottom() + metrics.sectionGap) - metrics.innerInset);
        List<DashboardTreeNode> roots = controller.activeTab() == DashboardTab.MOD_SETTINGS ? snapshot.settingRoots() : snapshot.macroRoots();
        int y = contentClip.y() - (int) controller.settingsScroll();
        for (DashboardTreeNode root : roots) {
            y = appendTree(scene, root, 0, contentClip, y, controller, editingNumberControlId, editingDraftValue, metrics);
        }
        int contentHeight = Math.max(contentClip.height(), y + (int) controller.settingsScroll() - contentClip.y());
        appendVerticalScrollMetadata(scene, "settings", contentClip, contentHeight, metrics);
        appendScrollbar(scene, "settings", contentClip, controller.settingsScroll(), contentHeight, metrics);
    }

    private int appendTree(UiScene scene, DashboardTreeNode node, int depth, UiRect clipRect, int startY, DashboardController controller,
                           @Nullable String editingNumberControlId, @Nullable String editingDraftValue, Metrics metrics) {
        int indent = depth * metrics.indentStep;
        int x = clipRect.x() + metrics.contentPadding + indent;
        int width = Math.max(metrics.minControlWidth, clipRect.width() - metrics.contentPadding * 2 - indent - metrics.scrollbarGutter);
        if (node.group()) {
            UiRect bounds = new UiRect(x, startY, width, metrics.groupHeight);
            scene.add(new UiNode(node.id(), UiNodeType.TREE_GROUP, UiPass.CONTROLS, bounds, clipRect,
                    Map.of("title", node.displayKey(), "summary", Objects.toString(node.summaryKey(), ""),
                            "expanded", controller.isExpanded(node.id()), "scale", metrics.uiScale)));
            startY += metrics.groupHeight + metrics.smallGap;
            if (controller.isExpanded(node.id())) {
                for (DashboardTreeNode child : node.children()) {
                    startY = appendTree(scene, child, depth + 1, clipRect, startY, controller, editingNumberControlId, editingDraftValue, metrics);
                }
            }
            return startY + metrics.groupGap;
        }

        UiRect bounds = new UiRect(x, startY, width, metrics.rowHeight);
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
        props.put("editing", node.controlId() != null && node.controlId().equals(editingNumberControlId));
        props.put("draftValue", node.controlId() != null && node.controlId().equals(editingNumberControlId) ? Objects.toString(editingDraftValue, "") : "");
        props.put("scale", metrics.uiScale);
        scene.add(new UiNode(node.id(), UiNodeType.TREE_CONTROL, UiPass.CONTROLS, bounds, clipRect, props));
        return startY + metrics.rowHeight + metrics.smallGap;
    }

    private void buildRightColumn(UiScene scene, DashboardViewSnapshot snapshot, DashboardController controller, UiRect shell, UiRect rightPanel,
                                  @Nullable UiRect collapsedDiagnosticsRect, Metrics metrics) {
        if (controller.diagnosticsPanelMode() == DiagnosticsPanelMode.COLLAPSED) {
            scene.add(new UiNode("metrics-panel", UiNodeType.PANEL, UiPass.PANELS, rightPanel, null,
                    Map.of("fill", 0xB8141A26, "border", 0xA9314157, "role", "panel")));
            buildMetrics(scene, snapshot, controller, rightPanel, metrics);
            if (collapsedDiagnosticsRect != null) {
                buildDiagnosticsCollapsed(scene, snapshot, controller, collapsedDiagnosticsRect, metrics);
            }
            return;
        }

        if (controller.diagnosticsPanelMode() == DiagnosticsPanelMode.EXPANDED) {
            scene.add(new UiNode("metrics-panel", UiNodeType.PANEL, UiPass.PANELS, rightPanel, null,
                    Map.of("fill", 0xB8141A26, "border", 0xA9314157, "role", "panel")));
            buildMetrics(scene, snapshot, controller, rightPanel, metrics);

            UiRect diagnosticsPanel = expandedDiagnosticsOverlayRect(shell, metrics);
            scene.add(new UiNode("diagnostics-panel", UiNodeType.PANEL, UiPass.OVERLAY, diagnosticsPanel, null,
                    Map.of("fill", 0xE6111721, "border", 0xCC3A5268, "role", "panel", "interactive", true)));
            buildDiagnosticsExpanded(scene, snapshot, controller, diagnosticsPanel, metrics, UiPass.OVERLAY);
            return;
        }

        scene.add(new UiNode("metrics-panel", UiNodeType.PANEL, UiPass.PANELS, rightPanel, null,
                Map.of("fill", 0xB8141A26, "border", 0xA9314157, "role", "panel")));
        buildMetrics(scene, snapshot, controller, rightPanel, metrics);

        UiRect diagnosticsPanel = expandedDiagnosticsOverlayRect(shell, metrics);
        scene.add(new UiNode("diagnostics-panel", UiNodeType.PANEL, UiPass.OVERLAY, diagnosticsPanel, null,
                Map.of("fill", 0xE6111721, "border", 0xCC3A5268, "role", "panel", "interactive", true)));
        buildDiagnosticsExpanded(scene, snapshot, controller, diagnosticsPanel, metrics, UiPass.OVERLAY);
    }

    private UiRect expandedDiagnosticsOverlayRect(UiRect shell, Metrics metrics) {
        int inset = metrics.innerInset;
        int overlayHeight = Math.max(metrics.minDiagnosticsHeight, Math.min(shell.height() - inset * 3, Math.round(shell.height() * 0.62f)));
        int y = Math.max(shell.y() + inset, shell.bottom() - inset - overlayHeight);
        return new UiRect(shell.x() + inset, y, shell.width() - inset * 2, overlayHeight);
    }
    private void buildMetrics(UiScene scene, DashboardViewSnapshot snapshot, DashboardController controller, UiRect metricsPanel, Metrics metrics) {
        scene.add(new UiNode("metrics-header", UiNodeType.HEADER, UiPass.PANELS,
                new UiRect(metricsPanel.x(), metricsPanel.y(), metricsPanel.width(), metrics.headerHeight), null,
                Map.of("title", "debug.dashboard.metrics", "fill", 0xD61A2330, "border", 0x7A37485F, "role", "header", "scale", metrics.uiScale)));

        UiRect contentClip = new UiRect(metricsPanel.x() + metrics.innerInset, metricsPanel.y() + metrics.headerHeight + metrics.sectionGap,
                metricsPanel.width() - metrics.innerInset * 2,
                metricsPanel.bottom() - (metricsPanel.y() + metrics.headerHeight + metrics.sectionGap) - metrics.innerInset);
        int contentWidth = contentClip.width() - metrics.scrollbarGutter;
        int columns = resolveMetricColumns(controller, contentWidth, metrics);

        scene.add(new UiNode("metrics-layout-toggle", UiNodeType.METRICS_LAYOUT_TOGGLE, UiPass.CONTROLS,
                new UiRect(metricsPanel.right() - metrics.innerInset - metrics.layoutToggleWidth, metricsPanel.y() + metrics.layoutToggleYOffset,
                        metrics.layoutToggleWidth, metrics.layoutToggleHeight), null,
                Map.of("layoutMode", controller.metricsLayoutMode().name(), "columns", columns, "scale", metrics.uiScale)));

        int y = contentClip.y() - (int) controller.metricsScroll();
        int columnGap = metrics.gap;
        int columnWidth = columns <= 1 ? contentWidth : Math.max(metrics.minMetricColumnWidth, (contentWidth - columnGap * (columns - 1)) / columns);
        int rows = columns <= 1 ? snapshot.summaryMetrics().size() : (int) Math.ceil(snapshot.summaryMetrics().size() / (double) columns);
        for (int index = 0; index < snapshot.summaryMetrics().size(); index++) {
            DashboardSummaryMetric metric = snapshot.summaryMetrics().get(index);
            int columnIndex = columns <= 1 ? 0 : index % columns;
            int rowIndex = columns <= 1 ? index : index / columns;
            int itemX = contentClip.x() + columnIndex * (columnWidth + columnGap);
            int itemY = y + rowIndex * (metrics.summaryRowHeight + metrics.smallGap);
            scene.add(new UiNode("metric/" + metric.id(), UiNodeType.METRIC_CARD, UiPass.CONTROLS,
                    new UiRect(itemX, itemY, columnWidth, metrics.summaryRowHeight), contentClip,
                    Map.of("title", metric.labelKey(), "value", metric.valueText(), "unit", metric.unitText(), "accent", metric.accentColor(),
                            "detail", metric.detailKey(), "scale", metrics.uiScale, "mode", "summary-row")));
        }
        y += Math.max(0, rows * (metrics.summaryRowHeight + metrics.smallGap));

        if (!snapshot.ratioMetrics().isEmpty()) {
            y += metrics.sectionGap;
        }
        for (DashboardRatioMetric ratioMetric : snapshot.ratioMetrics()) {
            scene.add(new UiNode("ratio/" + ratioMetric.id(), UiNodeType.METRIC_CARD, UiPass.CONTROLS,
                    new UiRect(contentClip.x(), y, contentWidth, metrics.ratioRowHeight), contentClip,
                    Map.of("title", ratioMetric.labelKey(), "mode", "ratio-row", "hidden", ratioMetric.hiddenCount(),
                            "visible", ratioMetric.visibleCount(), "total", ratioMetric.totalCount(), "ratio", ratioMetric.hiddenRatio(),
                            "accent", ratioMetric.accentColor(), "detail", ratioMetric.detailKey(), "scale", metrics.uiScale)));
            y += metrics.ratioRowHeight + metrics.sectionGap;
        }

        scene.add(new UiNode("frame-chart", UiNodeType.BAR_CHART, UiPass.CONTROLS,
                new UiRect(contentClip.x(), y, contentWidth, metrics.chartHeight), contentClip,
                Map.of("bars", snapshot.frameTimeHistory(), "title", "debug.dashboard.frame_history", "threshold", 33.0D,
                        "scale", metrics.uiScale, "mode", "frame-chart")));
        y += metrics.chartHeight + metrics.sectionGap;

        scene.add(new UiNode("macro-constants-header", UiNodeType.MACRO_SECTION_HEADER, UiPass.CONTROLS,
                new UiRect(contentClip.x(), y, contentWidth, metrics.groupHeight), contentClip,
                Map.of("title", "debug.dashboard.macro_constants", "expanded", controller.macroConstantsExpanded(), "scale", metrics.uiScale)));
        y += metrics.groupHeight + metrics.smallGap;

        if (controller.macroConstantsExpanded()) {
            for (DashboardMacroConstantView constant : snapshot.macroConstants()) {
                scene.add(new UiNode("macro-constant/" + constant.name() + "/" + constant.sourceText(), UiNodeType.MACRO_CONSTANT_ROW, UiPass.CONTROLS,
                        new UiRect(contentClip.x(), y, contentWidth, metrics.constantRowHeight), contentClip,
                        Map.of("name", constant.name(), "value", constant.value(), "flag", constant.flag(), "source", constant.sourceText(),
                                "type", constant.typeText(), "detail", constant.detailText(), "scale", metrics.uiScale, "mode", "macro-constant-row")));
                y += metrics.constantRowHeight + metrics.smallGap;
            }
        }

        int contentHeight = Math.max(contentClip.height(), y + (int) controller.metricsScroll() - contentClip.y());
        appendVerticalScrollMetadata(scene, "metrics", contentClip, contentHeight, metrics);
        appendScrollbar(scene, "metrics", contentClip, controller.metricsScroll(), contentHeight, metrics);
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

    private void buildDiagnosticsCollapsed(UiScene scene, DashboardViewSnapshot snapshot, DashboardController controller, UiRect diagnosticsRect, Metrics metrics) {
        boolean unreadAlerts = snapshot.latestAlertSequence() > controller.acknowledgedAlertSequence();
        Map<String, Object> headerProps = new LinkedHashMap<>();
        headerProps.put("title", "debug.dashboard.diagnostics");
        headerProps.put("mode", controller.diagnosticsPanelMode().name());
        headerProps.put("preview", snapshot.latestDiagnosticPreview());
        headerProps.put("warningCount", snapshot.warningCount());
        headerProps.put("errorCount", snapshot.errorCount());
        headerProps.put("unreadAlerts", unreadAlerts);
        headerProps.put("scale", metrics.uiScale);
        scene.add(new UiNode("diagnostics-header", UiNodeType.DIAGNOSTIC_HEADER, UiPass.CONTROLS, diagnosticsRect, null, headerProps));
    }

    private void buildDiagnosticsExpanded(UiScene scene, DashboardViewSnapshot snapshot, DashboardController controller, UiRect diagnosticsPanel, Metrics metrics, UiPass pass) {
        boolean unreadAlerts = snapshot.latestAlertSequence() > controller.acknowledgedAlertSequence();
        Map<String, Object> headerProps = new LinkedHashMap<>();
        headerProps.put("title", "debug.dashboard.diagnostics");
        headerProps.put("mode", controller.diagnosticsPanelMode().name());
        headerProps.put("preview", snapshot.latestDiagnosticPreview());
        headerProps.put("warningCount", snapshot.warningCount());
        headerProps.put("errorCount", snapshot.errorCount());
        headerProps.put("unreadAlerts", unreadAlerts);
        headerProps.put("scale", metrics.uiScale);
        scene.add(new UiNode("diagnostics-header", UiNodeType.DIAGNOSTIC_HEADER, pass,
                new UiRect(diagnosticsPanel.x(), diagnosticsPanel.y(), diagnosticsPanel.width(), metrics.diagnosticsHeaderHeight), null, headerProps));

        int controlsY = diagnosticsPanel.y() + metrics.titleRowHeight + metrics.smallGap + metrics.filterYOffset;
        int filterX = diagnosticsPanel.x() + metrics.innerInset;
        for (DiagnosticLevel level : DiagnosticLevel.values()) {
            scene.add(new UiNode("diag-filter/" + level.name(), UiNodeType.DIAGNOSTIC_FILTER, pass,
                    new UiRect(filterX, controlsY, metrics.filterWidth, metrics.filterHeight), null,
                    Map.of("level", level.name(), "active", controller.diagnosticFilters().contains(level), "scale", metrics.uiScale)));
            filterX += metrics.filterWidth + metrics.smallGap;
        }

        int stateX = diagnosticsPanel.right() - metrics.innerInset - (metrics.stateWidth * 2 + metrics.smallGap);
        scene.add(new UiNode("diag-state-expand", UiNodeType.DIAGNOSTIC_STATE, pass,
                new UiRect(stateX, controlsY, metrics.stateWidth, metrics.filterHeight), null,
                Map.of("mode", "EXPANDED", "scale", metrics.uiScale)));
        scene.add(new UiNode("diag-state-collapse", UiNodeType.DIAGNOSTIC_STATE, pass,
                new UiRect(stateX + metrics.stateWidth + metrics.smallGap, controlsY, metrics.stateWidth, metrics.filterHeight), null,
                Map.of("mode", "COLLAPSED", "scale", metrics.uiScale)));

        int verticalScrollbarReserve = metrics.scrollbarGutter;
        int baseClipHeight = diagnosticsPanel.bottom() - (diagnosticsPanel.y() + metrics.diagnosticsHeaderHeight + metrics.sectionGap) - metrics.innerInset;
        int estimatedContentWidth = estimateDiagnosticsContentWidth(snapshot, metrics);
        int viewportWidth = diagnosticsPanel.width() - metrics.innerInset * 2 - verticalScrollbarReserve;
        boolean needsHorizontalScrollbar = estimatedContentWidth > viewportWidth;
        int clipHeight = needsHorizontalScrollbar ? baseClipHeight - metrics.horizontalScrollbarHeight - metrics.smallGap : baseClipHeight;
        UiRect clip = new UiRect(diagnosticsPanel.x() + metrics.innerInset, diagnosticsPanel.y() + metrics.diagnosticsHeaderHeight + metrics.sectionGap,
                diagnosticsPanel.width() - metrics.innerInset * 2,
                Math.max(metrics.minDiagnosticsClipHeight, clipHeight));

        int y = clip.y() - (int) controller.diagnosticsScroll();
        for (var line : snapshot.diagnostics()) {
            if (!controller.accepts(line.level())) {
                continue;
            }
            int rowWidth = Math.max(viewportWidth, estimatedContentWidth);
            scene.add(new UiNode("log/" + y, UiNodeType.LOG_LINE, pass,
                    new UiRect(clip.x() - (int) controller.diagnosticsHorizontalScroll(), y, rowWidth, metrics.logLineHeight), clip,
                    Map.of("time", line.timeText(), "level", line.level().name(), "module", line.moduleId(), "message", line.message(),
                            "repeat", line.repeatCount(), "scale", metrics.uiScale)));
            y += metrics.logLineHeight + metrics.smallGap;
        }

        int contentHeight = Math.max(clip.height(), y + (int) controller.diagnosticsScroll() - clip.y());
        appendVerticalScrollMetadata(scene, "diagnostics", clip, contentHeight, metrics);
        appendScrollbar(scene, "diagnostics", clip, controller.diagnosticsScroll(), contentHeight, metrics);

        if (needsHorizontalScrollbar) {
            UiRect horizontalTrackRect = new UiRect(clip.x(), clip.bottom() + metrics.smallGap, viewportWidth, metrics.horizontalScrollbarHeight);
            appendHorizontalScrollMetadata(scene, "diagnostics-x", horizontalTrackRect, viewportWidth, estimatedContentWidth, metrics);
            appendHorizontalScrollbar(scene, "diagnostics-x", horizontalTrackRect, controller.diagnosticsHorizontalScroll(), viewportWidth, estimatedContentWidth, metrics);
        }
    }

    private int estimateDiagnosticsContentWidth(DashboardViewSnapshot snapshot, Metrics metrics) {
        int maxWidth = metrics.diagnosticBaseWidth;
        for (var line : snapshot.diagnostics()) {
            int width = metrics.diagnosticBaseWidth
                    + Math.max(0, line.moduleId().length()) * metrics.charWidth
                    + Math.max(0, line.message().length()) * metrics.charWidth
                    + (line.repeatCount() > 1 ? (4 + String.valueOf(line.repeatCount()).length()) * metrics.charWidth : 0);
            maxWidth = Math.max(maxWidth, width);
        }
        return maxWidth;
    }

    private void appendVerticalScrollMetadata(UiScene scene, String area, UiRect clipRect, int contentHeight, Metrics metrics) {
        scene.add(new UiNode("scroll-region/" + area, UiNodeType.PANEL, UiPass.OVERLAY, clipRect, null,
                Map.of("fill", 0, "role", "scroll-region", "scrollArea", area, "viewportHeight", clipRect.height(),
                        "contentHeight", contentHeight, "scale", metrics.uiScale)));
    }

    private void appendHorizontalScrollMetadata(UiScene scene, String area, UiRect trackRect, int viewportWidth, int contentWidth, Metrics metrics) {
        scene.add(new UiNode("scroll-region/" + area, UiNodeType.PANEL, UiPass.OVERLAY, trackRect, null,
                Map.of("fill", 0, "role", "scroll-region-x", "scrollArea", area, "viewportWidth", viewportWidth,
                        "contentWidth", contentWidth, "scale", metrics.uiScale)));
    }

    private void appendScrollbar(UiScene scene, String area, UiRect clipRect, double scroll, int contentHeight, Metrics metrics) {
        if (contentHeight <= clipRect.height()) {
            return;
        }

        UiRect track = new UiRect(clipRect.right() - metrics.scrollbarWidth, clipRect.y(), metrics.scrollbarWidth, clipRect.height());
        int thumbHeight = Math.max(metrics.scrollbarThumbMinHeight,
                Math.round((clipRect.height() * (clipRect.height() / (float) contentHeight))));
        int maxScroll = Math.max(1, contentHeight - clipRect.height());
        int travel = Math.max(1, track.height() - thumbHeight);
        int thumbY = track.y() + Math.round((float) (Math.min(scroll, maxScroll) / maxScroll) * travel);

        scene.add(new UiNode("scrollbar-track/" + area, UiNodeType.PANEL, UiPass.OVERLAY, track, null,
                Map.of("fill", 0x2E273242, "role", "scrollbar-track", "interactive", true, "scrollArea", area,
                        "viewportHeight", clipRect.height(), "contentHeight", contentHeight, "thumbHeight", thumbHeight, "scale", metrics.uiScale)));
        scene.add(new UiNode("scrollbar-thumb/" + area, UiNodeType.PANEL, UiPass.OVERLAY,
                new UiRect(track.x() + 1, thumbY, Math.max(3, track.width() - 2), thumbHeight), null,
                Map.of("fill", 0xCC5E738B, "border", 0xFF8EA1B8, "role", "scrollbar-thumb", "interactive", true,
                        "scrollArea", area, "viewportHeight", clipRect.height(), "contentHeight", contentHeight, "thumbHeight", thumbHeight, "scale", metrics.uiScale)));
    }

    private void appendHorizontalScrollbar(UiScene scene, String area, UiRect trackRect, double scroll, int viewportWidth, int contentWidth, Metrics metrics) {
        if (contentWidth <= viewportWidth) {
            return;
        }
        int thumbWidth = Math.max(metrics.horizontalScrollbarThumbMinWidth,
                Math.round((trackRect.width() * (trackRect.width() / (float) contentWidth))));
        int maxScroll = Math.max(1, contentWidth - viewportWidth);
        int travel = Math.max(1, trackRect.width() - thumbWidth);
        int thumbX = trackRect.x() + Math.round((float) (Math.min(scroll, maxScroll) / maxScroll) * travel);

        scene.add(new UiNode("scrollbar-track/" + area, UiNodeType.PANEL, UiPass.OVERLAY, trackRect, null,
                Map.of("fill", 0x2E273242, "role", "scrollbar-track-x", "interactive", true, "scrollArea", area,
                        "viewportWidth", viewportWidth, "contentWidth", contentWidth, "thumbWidth", thumbWidth, "scale", metrics.uiScale)));
        scene.add(new UiNode("scrollbar-thumb/" + area, UiNodeType.PANEL, UiPass.OVERLAY,
                new UiRect(thumbX, trackRect.y() + 1, thumbWidth, Math.max(3, trackRect.height() - 2)), null,
                Map.of("fill", 0xCC5E738B, "border", 0xFF8EA1B8, "role", "scrollbar-thumb-x", "interactive", true,
                        "scrollArea", area, "viewportWidth", viewportWidth, "contentWidth", contentWidth, "thumbWidth", thumbWidth, "scale", metrics.uiScale)));
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
        private final int diagnosticsHeaderHeight;
        private final int titleRowHeight;
        private final int filterWidth;
        private final int filterHeight;
        private final int filterYOffset;
        private final int stateWidth;
        private final int logLineHeight;
        private final int layoutToggleWidth;
        private final int layoutToggleHeight;
        private final int layoutToggleYOffset;
        private final int collapsedHeight;
        private final int sectionGap;
        private final int gap;
        private final int smallGap;
        private final int indentStep;
        private final int minControlWidth;
        private final int minPanelWidth;
        private final int minPanelHeight;
        private final int minMetricsHeight;
        private final int minDiagnosticsHeight;
        private final int minDiagnosticsClipHeight;
        private final int minMetricColumnWidth;
        private final int diagnosticBaseWidth;
        private final int charWidth;
        private final int scrollbarWidth;
        private final int scrollbarGutter;
        private final int scrollbarThumbMinHeight;
        private final int horizontalScrollbarHeight;
        private final int horizontalScrollbarThumbMinWidth;

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
            this.titleRowHeight = scaled(26);
            this.filterHeight = scaled(20);
            this.filterYOffset = scaled(7);
            this.diagnosticsHeaderHeight = this.titleRowHeight + this.filterHeight + scaled(18);
            this.filterWidth = Math.max(scaled(48), screenWidth / 28);
            this.stateWidth = scaled(26);
            this.logLineHeight = scaled(20);
            this.layoutToggleWidth = scaled(40);
            this.layoutToggleHeight = scaled(18);
            this.layoutToggleYOffset = Math.max(scaled(6), (this.headerHeight - this.layoutToggleHeight) / 2);
            this.collapsedHeight = scaled(38);
            this.sectionGap = scaled(8);
            this.gap = scaled(6);
            this.smallGap = scaled(4);
            this.indentStep = scaled(16);
            this.minControlWidth = scaled(96);
            this.minPanelWidth = scaled(180);
            this.minPanelHeight = scaled(160);
            this.minMetricsHeight = scaled(180);
            this.minDiagnosticsHeight = scaled(120);
            this.minDiagnosticsClipHeight = scaled(40);
            this.minMetricColumnWidth = scaled(110);
            this.charWidth = scaled(6);
            this.diagnosticBaseWidth = scaled(180);
            this.scrollbarWidth = scaled(8);
            this.scrollbarGutter = scaled(14);
            this.scrollbarThumbMinHeight = scaled(22);
            this.horizontalScrollbarHeight = scaled(8);
            this.horizontalScrollbarThumbMinWidth = scaled(24);
        }

        private int scaled(int base) {
            return Math.max(1, Math.round(base * uiScale));
        }
    }
}






