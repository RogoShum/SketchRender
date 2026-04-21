package rogo.sketch.core.dashboard;

public record DashboardMemoryDomainMetric(
        String id,
        String labelKey,
        String liveText,
        String reservedText,
        String peakText,
        String budgetText,
        String usagePercentText,
        String peakPercentText,
        String fragmentationText,
        double usageRatio,
        double peakRatio,
        double budgetUsageRatio,
        int accentColor,
        String detailKey
) {
}
