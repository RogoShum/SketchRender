package rogo.sketch.core.backend;

public record BackendCapabilities(
        boolean workerLanesSupported,
        boolean uploadWorkerSupported,
        boolean computeWorkerSupported,
        boolean asyncUploadPreparationSupported
) {
    public static final BackendCapabilities NONE = new BackendCapabilities(false, false, false, false);
}

