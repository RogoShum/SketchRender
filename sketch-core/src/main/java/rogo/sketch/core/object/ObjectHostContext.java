package rogo.sketch.core.object;

import rogo.sketch.core.graphics.ecs.GraphicsWorld;
import rogo.sketch.core.pipeline.GraphicsPipeline;

/**
 * Spawn/sync context passed to object graphics factories and augmentors.
 */
public record ObjectHostContext(
        GraphicsPipeline<?> pipeline,
        GraphicsWorld graphicsWorld,
        ObjectHostKind hostKind,
        Object exactTypeKey,
        int logicTick
) {
    public static ObjectHostContext of(
            GraphicsPipeline<?> pipeline,
            ObjectHostKind hostKind,
            Object exactTypeKey,
            int logicTick) {
        return new ObjectHostContext(
                pipeline,
                pipeline != null ? pipeline.graphicsWorld() : null,
                hostKind,
                exactTypeKey,
                logicTick);
    }
}
