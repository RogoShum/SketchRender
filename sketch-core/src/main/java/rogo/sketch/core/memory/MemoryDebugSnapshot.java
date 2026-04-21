package rogo.sketch.core.memory;

import java.util.List;

public record MemoryDebugSnapshot(
        long totalLiveBytes,
        long totalReservedBytes,
        long totalPeakBytes,
        double allocBytesPerSecond,
        double freeBytesPerSecond,
        MemoryBudget totalBudget,
        double totalBudgetUsageRatio,
        List<MemoryDomainSnapshot> domains,
        List<MemoryTimelineRecord> timeline
) {
    private static final MemoryDebugSnapshot EMPTY = new MemoryDebugSnapshot(
            0L,
            0L,
            0L,
            0.0D,
            0.0D,
            MemoryBudget.unbounded(),
            0.0D,
            List.of(),
            List.of());

    public MemoryDebugSnapshot {
        domains = List.copyOf(domains);
        timeline = List.copyOf(timeline);
    }

    public static MemoryDebugSnapshot empty() {
        return EMPTY;
    }
}
