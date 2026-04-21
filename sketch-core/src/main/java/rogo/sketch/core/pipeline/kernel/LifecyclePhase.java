package rogo.sketch.core.pipeline.kernel;

/**
 * Fixed lifecycle windows used by the first V4C dual-track kernel.
 */
public enum LifecyclePhase {
    SYNC_COMMIT,
    SYNC_PREPARE,
    SYNC_PRE_BUILD,
    ASYNC_BUILD,
    ASYNC_POST_BUILD
}
