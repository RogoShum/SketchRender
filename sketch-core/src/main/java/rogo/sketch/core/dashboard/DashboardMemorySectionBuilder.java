package rogo.sketch.core.dashboard;

import rogo.sketch.core.memory.MemoryDebugSnapshot;
import rogo.sketch.core.memory.MemoryDomainSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DashboardMemorySectionBuilder {
    private static final long KIB = 1024L;
    private static final long MIB = KIB * 1024L;
    private static final long GIB = MIB * 1024L;

    public DashboardMemorySection build(MemoryDebugSnapshot snapshot) {
        if (snapshot == null) {
            return DashboardMemorySection.empty();
        }

        double worstFragmentation = snapshot.domains().stream()
                .mapToDouble(MemoryDomainSnapshot::fragmentationRatio)
                .max()
                .orElse(0.0D);

        List<DashboardSummaryMetric> summaryMetrics = List.of(
                new DashboardSummaryMetric(
                        "memory-section-total-live",
                        "debug.dashboard.memory.total_live",
                        formatBytes(snapshot.totalLiveBytes()),
                        "",
                        0xFF3B82F6,
                        "debug.dashboard.memory.total_live.detail"),
                new DashboardSummaryMetric(
                        "memory-section-total-peak",
                        "debug.dashboard.memory.total_peak",
                        formatBytes(snapshot.totalPeakBytes()),
                        "",
                        0xFF6366F1,
                        "debug.dashboard.memory.total_peak.detail"),
                new DashboardSummaryMetric(
                        "memory-section-alloc-rate",
                        "debug.dashboard.memory.alloc_rate",
                        formatBytesPerSecond(snapshot.allocBytesPerSecond()),
                        "",
                        0xFF10B981,
                        "debug.dashboard.memory.alloc_rate.detail"),
                new DashboardSummaryMetric(
                        "memory-section-free-rate",
                        "debug.dashboard.memory.free_rate",
                        formatBytesPerSecond(snapshot.freeBytesPerSecond()),
                        "",
                        0xFF14B8A6,
                        "debug.dashboard.memory.free_rate.detail"),
                new DashboardSummaryMetric(
                        "memory-section-budget-usage",
                        "debug.dashboard.memory.budget_usage",
                        formatPercent(snapshot.totalBudgetUsageRatio()),
                        "",
                        0xFFF59E0B,
                        "debug.dashboard.memory.budget_usage.detail"),
                new DashboardSummaryMetric(
                        "memory-section-fragmentation",
                        "debug.dashboard.memory.fragmentation",
                        formatPercent(worstFragmentation),
                        "",
                        0xFFA855F7,
                        "debug.dashboard.memory.fragmentation.detail"));

        List<DashboardMemoryDomainMetric> domainMetrics = new ArrayList<>();
        for (MemoryDomainSnapshot domainSnapshot : snapshot.domains()) {
            double peakRatio = 0.0D;
            if (domainSnapshot.budget().bounded() && domainSnapshot.budget().limitBytes() > 0L) {
                peakRatio = Math.max(0.0D, Math.min(1.0D, domainSnapshot.peakBytes() / (double) domainSnapshot.budget().limitBytes()));
            }
            domainMetrics.add(new DashboardMemoryDomainMetric(
                    "memory-domain/" + domainSnapshot.domain().name().toLowerCase(Locale.ROOT),
                    domainSnapshot.domain().displayKey(),
                    formatBytes(domainSnapshot.liveBytes()),
                    formatBytes(domainSnapshot.reservedBytes()),
                    formatBytes(domainSnapshot.peakBytes()),
                    domainSnapshot.budget().bounded()
                            ? formatPercent(domainSnapshot.budgetUsageRatio())
                            : "-",
                    formatPercent(domainSnapshot.budgetUsageRatio()),
                    formatPercent(peakRatio),
                    formatPercent(domainSnapshot.fragmentationRatio()),
                    domainSnapshot.budgetUsageRatio(),
                    peakRatio,
                    domainSnapshot.budgetUsageRatio(),
                    domainSnapshot.domain().accentColor(),
                    "debug.dashboard.memory.domain.detail"));
        }

        List<Double> timelineValues = snapshot.timeline().stream()
                .map(record -> (double) record.liveBytes())
                .toList();
        double timelineThreshold = snapshot.totalBudget().bounded()
                ? snapshot.totalBudget().limitBytes()
                : Double.MAX_VALUE;

        return new DashboardMemorySection(
                "debug.dashboard.memory.section",
                summaryMetrics,
                domainMetrics,
                "debug.dashboard.memory.timeline",
                timelineValues,
                timelineThreshold);
    }

    public static String formatBytes(long bytes) {
        long absBytes = Math.abs(bytes);
        if (absBytes >= GIB) {
            return formatBinary(bytes / (double) GIB, "GiB");
        }
        if (absBytes >= MIB) {
            return formatBinary(bytes / (double) MIB, "MiB");
        }
        if (absBytes >= KIB) {
            return formatBinary(bytes / (double) KIB, "KiB");
        }
        return bytes + " B";
    }

    public static String formatBytesPerSecond(double bytesPerSecond) {
        double absBytes = Math.abs(bytesPerSecond);
        if (absBytes >= GIB) {
            return formatBinary(bytesPerSecond / GIB, "GiB/s");
        }
        if (absBytes >= MIB) {
            return formatBinary(bytesPerSecond / MIB, "MiB/s");
        }
        if (absBytes >= KIB) {
            return formatBinary(bytesPerSecond / KIB, "KiB/s");
        }
        return String.format(Locale.ROOT, "%.1f B/s", bytesPerSecond);
    }

    public static String formatPercent(double ratio) {
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0D);
    }

    private static String formatBinary(double value, String unit) {
        return String.format(Locale.ROOT, "%.1f %s", value, unit);
    }
}
