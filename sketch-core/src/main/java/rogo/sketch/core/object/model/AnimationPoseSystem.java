package rogo.sketch.core.object.model;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsWorld;

/**
 * Stage 9 animation pose skeleton. The actual pose solving remains for the
 * later animation integration pass, but the ECS-facing query shape is fixed.
 */
public final class AnimationPoseSystem {
    public boolean hasAnimatedPose(GraphicsWorld world, GraphicsEntityId entityId) {
        return world != null
                && entityId != null
                && world.component(entityId, GraphicsBuiltinComponents.ANIMATION_STATE) != null
                && world.component(entityId, GraphicsBuiltinComponents.SKELETON_POSE) != null;
    }

    public @Nullable GraphicsBuiltinComponents.SkeletonPoseComponent currentPose(
            GraphicsWorld world,
            GraphicsEntityId entityId) {
        if (world == null || entityId == null) {
            return null;
        }
        return world.component(entityId, GraphicsBuiltinComponents.SKELETON_POSE);
    }
}
