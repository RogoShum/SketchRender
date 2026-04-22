package rogo.sketch.core.shader.uniform;

/**
 * Controls when a uniform getter is evaluated in the frame pipeline.
 */
public enum UniformCaptureTiming {
    /**
     * Captured once on the sync thread during {@code SyncPreparePass} and replayed during async build.
     */
    FRAME_SYNC,

    /**
     * Safe to evaluate on the async build thread while packets are being compiled.
     */
    BUILD_ASYNC_SAFE,

    /**
     * Deferred until draw/dispatch execution on the backend thread.
     */
    PER_DRAW_DEFERRED
}
