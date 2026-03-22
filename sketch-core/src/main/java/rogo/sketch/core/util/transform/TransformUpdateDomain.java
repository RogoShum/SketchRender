package rogo.sketch.core.util.transform;

/**
 * Lifecycle domain for authored transform updates.
 */
public enum TransformUpdateDomain {
    SYNC_TICK,
    ASYNC_TICK,
    SYNC_FRAME,
    STATIC
}
