package rogo.sketch.core.api.graphics;

public enum SubmissionCapability {
    DIRECT_ONLY,
    DIRECT_BATCHABLE,
    INDIRECT_READY,
    GPU_CULL_READY;

    public boolean supportsDirectBatching() {
        return this != DIRECT_ONLY;
    }

    public boolean supportsIndirect() {
        return this == INDIRECT_READY || this == GPU_CULL_READY;
    }

    public boolean supportsGpuCull() {
        return this == GPU_CULL_READY;
    }
}
