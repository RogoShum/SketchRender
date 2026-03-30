package rogo.sketch.core.debugger;

public record DashboardMetricCard(
        String id,
        String labelKey,
        String valueText,
        String unitText,
        int accentColor
) {
}
