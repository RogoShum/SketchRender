package rogo.sketch.core.pipeline.flow.dirty;

/**
 * Reason category for dirty instance updates.
 */
public enum DirtyReason {
    NOT,

    RENDER_STATE,
    MESH,
    BOUNDS,
    UNKNOWN
}