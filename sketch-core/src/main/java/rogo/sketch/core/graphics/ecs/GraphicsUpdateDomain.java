package rogo.sketch.core.graphics.ecs;

/**
 * Shared lifecycle domain for authored ECS component updates.
 */
public enum GraphicsUpdateDomain {
    SYNC_TICK,
    ASYNC_TICK,
    SYNC_FRAME,
    STATIC
}
