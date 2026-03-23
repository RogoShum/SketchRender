package rogo.sketch.module.transform.manager;

/**
 * Lifecycle domain for authored transform updates.
 */
public enum TransformUpdateDomain {
    SYNC_TICK,
    ASYNC_TICK,
    SYNC_FRAME,
    STATIC
}