package rogo.sketch.core.shader.uniform;

/**
 * Legacy compatibility shim for the pre-Phase-2.9 two-state uniform model.
 */
@Deprecated
public enum UniformUpdateDomain {
    BUILD_SNAPSHOT(UniformCaptureTiming.BUILD_ASYNC_SAFE),
    FRAME_LIVE(UniformCaptureTiming.PER_DRAW_DEFERRED);

    private final UniformCaptureTiming timing;

    UniformUpdateDomain(UniformCaptureTiming timing) {
        this.timing = timing;
    }

    public UniformCaptureTiming timing() {
        return timing;
    }

    public static UniformUpdateDomain fromTiming(UniformCaptureTiming timing) {
        if (timing == UniformCaptureTiming.BUILD_ASYNC_SAFE) {
            return BUILD_SNAPSHOT;
        }
        return FRAME_LIVE;
    }
}
