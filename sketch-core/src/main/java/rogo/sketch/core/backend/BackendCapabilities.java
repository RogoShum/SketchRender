package rogo.sketch.core.backend;

public record BackendCapabilities(
        boolean workerLanesSupported,
        boolean uploadWorkerSupported,
        boolean computeWorkerSupported
) {
    public static final BackendCapabilities NONE = new BackendCapabilities(false, false, false);
}
