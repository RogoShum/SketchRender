package rogo.sketch.core.memory;

public enum MemoryDomain {
    CPU_NATIVE("debug.dashboard.memory.domain.cpu_native", 0xFF10B981, 64L * 1024L * 1024L),
    CPU_INDIRECT_STAGING("debug.dashboard.memory.domain.cpu_indirect_staging", 0xFF14B8A6, 16L * 1024L * 1024L),
    GPU_PERSISTENT_MAPPED("debug.dashboard.memory.domain.gpu_persistent_mapped", 0xFF3B82F6, 128L * 1024L * 1024L),
    GPU_INDIRECT_BUFFER("debug.dashboard.memory.domain.gpu_indirect_buffer", 0xFF6366F1, 32L * 1024L * 1024L),
    GPU_GEOMETRY_ARENA("debug.dashboard.memory.domain.gpu_geometry_arena", 0xFFA855F7, 128L * 1024L * 1024L),
    GPU_DESCRIPTOR("debug.dashboard.memory.domain.gpu_descriptor", 0xFFF59E0B, 32L * 1024L * 1024L),
    GPU_TEXTURE("debug.dashboard.memory.domain.gpu_texture", 0xFFEF4444, 512L * 1024L * 1024L),
    FRAME_TRANSIENT("debug.dashboard.memory.domain.frame_transient", 0xFF94A3B8, 64L * 1024L * 1024L);

    private final String displayKey;
    private final int accentColor;
    private final MemoryBudget defaultBudget;

    MemoryDomain(String displayKey, int accentColor, long defaultBudgetBytes) {
        this.displayKey = displayKey;
        this.accentColor = accentColor;
        this.defaultBudget = MemoryBudget.ofBytes(defaultBudgetBytes);
    }

    public String displayKey() {
        return displayKey;
    }

    public int accentColor() {
        return accentColor;
    }

    public MemoryBudget defaultBudget() {
        return defaultBudget;
    }
}
