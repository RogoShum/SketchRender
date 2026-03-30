package rogo.sketch.core.dashboard;

public record DashboardSummaryMetric(
        String id,
        String labelKey,
        String valueText,
        String unitText,
        int accentColor,
        String detailKey
) {
}
