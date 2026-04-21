package rogo.sketch.core.backend;

public enum BackendWorkerLane {
    RENDER_ASYNC,
    TICK_ASYNC,
    UPLOAD_ASYNC,
    COMPUTE_ASYNC,
    OFFSCREEN_GRAPHICS_ASYNC
}
