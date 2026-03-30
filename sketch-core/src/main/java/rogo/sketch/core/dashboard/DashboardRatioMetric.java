package rogo.sketch.core.dashboard;

public record DashboardRatioMetric(
        String id,
        String labelKey,
        int hiddenCount,
        int visibleCount,
        int totalCount,
        double hiddenRatio,
        int accentColor,
        String detailKey
) {
}
