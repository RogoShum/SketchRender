package rogo.sketch.core.dashboard;

import rogo.sketch.core.debugger.DashboardDiagnosticLine;
import rogo.sketch.core.debugger.DashboardTreeNode;

import java.util.List;

public record DashboardViewSnapshot(
        List<DashboardTreeNode> settingRoots,
        List<DashboardTreeNode> macroRoots,
        List<DashboardSummaryMetric> summaryMetrics,
        DashboardMemorySection memorySection,
        List<DashboardRatioMetric> ratioMetrics,
        List<Double> frameTimeHistory,
        List<DashboardMacroConstantView> macroConstants,
        List<DashboardDiagnosticLine> diagnostics,
        String latestDiagnosticPreview,
        int warningCount,
        int errorCount,
        int alertCount,
        long latestAlertSequence
) {
    public DashboardViewSnapshot {
        settingRoots = List.copyOf(settingRoots);
        macroRoots = List.copyOf(macroRoots);
        summaryMetrics = List.copyOf(summaryMetrics);
        memorySection = memorySection != null ? memorySection : DashboardMemorySection.empty();
        ratioMetrics = List.copyOf(ratioMetrics);
        frameTimeHistory = List.copyOf(frameTimeHistory);
        macroConstants = List.copyOf(macroConstants);
        diagnostics = List.copyOf(diagnostics);
    }
}
