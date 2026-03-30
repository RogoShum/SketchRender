package rogo.sketch.core.debugger;

import rogo.sketch.core.pipeline.module.diagnostic.DiagnosticLevel;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class DashboardController {
    private DashboardTab activeTab = DashboardTab.MOD_SETTINGS;
    private DiagnosticsPanelMode diagnosticsPanelMode = DiagnosticsPanelMode.COLLAPSED;
    private final Set<String> expandedTreeNodes = new HashSet<>();
    private final EnumSet<DiagnosticLevel> diagnosticFilters = EnumSet.noneOf(DiagnosticLevel.class);
    private boolean macroConstantsExpanded = true;
    private MetricsLayoutMode metricsLayoutMode = MetricsLayoutMode.AUTO;
    private long acknowledgedAlertSequence;
    private String openChoiceControlId;
    private double settingsScroll;
    private double metricsScroll;
    private double diagnosticsScroll;
    private double diagnosticsHorizontalScroll;

    public DashboardTab activeTab() {
        return activeTab;
    }

    public void setActiveTab(DashboardTab activeTab) {
        this.activeTab = activeTab;
    }

    public DiagnosticsPanelMode diagnosticsPanelMode() {
        return diagnosticsPanelMode;
    }

    public void setDiagnosticsPanelMode(DiagnosticsPanelMode diagnosticsPanelMode) {
        this.diagnosticsPanelMode = diagnosticsPanelMode;
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

    public double diagnosticsHorizontalScroll() {
        return diagnosticsHorizontalScroll;
    }

    public void setDiagnosticsHorizontalScroll(double diagnosticsHorizontalScroll) {
        this.diagnosticsHorizontalScroll = Math.max(0.0D, diagnosticsHorizontalScroll);
    }
}

