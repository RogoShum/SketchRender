package rogo.sketch.core.memory;

public record MemoryDomainSnapshot(
        MemoryDomain domain,
        long liveBytes,
        long reservedBytes,
        long peakBytes,
        MemoryBudget budget,
        double budgetUsageRatio,
        double fragmentationRatio
) {
}
