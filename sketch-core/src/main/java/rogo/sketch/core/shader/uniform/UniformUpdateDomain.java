package rogo.sketch.core.shader.uniform;

/**
 * Controls when a uniform getter is evaluated.
 *
 * BUILD_SNAPSHOT values are captured during async build and replayed from the
 * packet snapshot during sync execution.
 *
 * FRAME_LIVE values are evaluated against the current render context during
 * execution.
 */
public enum UniformUpdateDomain {
    BUILD_SNAPSHOT,
    FRAME_LIVE
}
