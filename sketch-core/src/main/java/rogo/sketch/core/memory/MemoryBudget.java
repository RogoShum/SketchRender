package rogo.sketch.core.memory;

public record MemoryBudget(long limitBytes) {
    private static final MemoryBudget UNBOUNDED = new MemoryBudget(0L);

    public static MemoryBudget unbounded() {
        return UNBOUNDED;
    }

    public static MemoryBudget ofBytes(long limitBytes) {
        return limitBytes <= 0L ? UNBOUNDED : new MemoryBudget(limitBytes);
    }

    public boolean bounded() {
        return limitBytes > 0L;
    }

    public double usageRatio(long bytes) {
        if (!bounded() || bytes <= 0L) {
            return 0.0D;
        }
        return Math.max(0.0D, bytes / (double) limitBytes);
    }
}
