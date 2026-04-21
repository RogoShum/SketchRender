package rogo.sketch.core.object.model;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsWorld;

/**
 * Stage 9 model resolver skeleton. This keeps model-instance and render-part
 * data on a stable ECS surface before mesh swapping is wired into the render
 * pipeline.
 */
public final class ModelResolveSystem {
    public boolean hasResolvedModel(GraphicsWorld world, GraphicsEntityId entityId) {
        return world != null
                && entityId != null
                && world.component(entityId, GraphicsBuiltinComponents.MODEL_INSTANCE) != null
                && world.component(entityId, GraphicsBuiltinComponents.RENDER_PART) != null;
    }

    public @Nullable GraphicsBuiltinComponents.ModelInstanceComponent modelInstance(
            GraphicsWorld world,
            GraphicsEntityId entityId) {
        if (world == null || entityId == null) {
            return null;
        }
        return world.component(entityId, GraphicsBuiltinComponents.MODEL_INSTANCE);
    }
}
