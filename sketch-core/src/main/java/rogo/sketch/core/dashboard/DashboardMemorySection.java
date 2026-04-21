package rogo.sketch.core.dashboard;

import java.util.List;

public record DashboardMemorySection(
        String titleKey,
        List<DashboardSummaryMetric> summaryMetrics,
        List<DashboardMemoryDomainMetric> domainMetrics,
        String timelineTitleKey,
        List<Double> timelineValues,
        double timelineThreshold
) {
    private static final DashboardMemorySection EMPTY = new DashboardMemorySection(
            "",
            List.of(),
            List.of(),
            "",
            List.of(),
            Double.MAX_VALUE);

    public DashboardMemorySection {
        summaryMetrics = List.copyOf(summaryMetrics);
        domainMetrics = List.copyOf(domainMetrics);
        timelineValues = List.copyOf(timelineValues);
    }

    public static DashboardMemorySection empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return titleKey.isEmpty()
                && summaryMetrics.isEmpty()
                && domainMetrics.isEmpty()
                && timelineValues.isEmpty();
    }
}
