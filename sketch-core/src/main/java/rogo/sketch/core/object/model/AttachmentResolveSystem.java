package rogo.sketch.core.object.model;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsWorld;

/**
 * Stage 9 attachment resolver skeleton. Root/sub-mesh linkage is declared here
 * so later passes can resolve bone-local transforms without exposing raw root
 * entity ids to external callers.
 */
public final class AttachmentResolveSystem {
    public boolean isAttachment(GraphicsWorld world, GraphicsEntityId entityId) {
        return world != null
                && entityId != null
                && world.component(entityId, GraphicsBuiltinComponents.ROOT_REFERENCE) != null
                && world.component(entityId, GraphicsBuiltinComponents.ATTACHMENT) != null;
    }

    public @Nullable GraphicsEntityId resolveRoot(GraphicsWorld world, GraphicsEntityId entityId) {
        if (world == null || entityId == null) {
            return null;
        }
        GraphicsBuiltinComponents.RootReferenceComponent rootReference =
                world.component(entityId, GraphicsBuiltinComponents.ROOT_REFERENCE);
        return rootReference != null ? rootReference.rootEntityId() : null;
    }
}
