package rogo.sketch.core.backend;

public record BackendCapabilities(
        boolean workerLanesSupported,
        boolean uploadWorkerSupported,
        boolean computeWorkerSupported,
        boolean offscreenGraphicsWorkerSupported,
        boolean asyncUploadPreparationSupported
) {
    public static final BackendCapabilities NONE = new BackendCapabilities(false, false, false, false, false);

    public boolean supportsLane(BackendWorkerLane lane) {
        if (lane == null || !workerLanesSupported) {
            return false;
        }
        return switch (lane) {
            case RENDER_ASYNC, TICK_ASYNC -> true;
            case UPLOAD_ASYNC -> uploadWorkerSupported;
            case COMPUTE_ASYNC -> computeWorkerSupported;
            case OFFSCREEN_GRAPHICS_ASYNC -> offscreenGraphicsWorkerSupported;
        };
    }
}

