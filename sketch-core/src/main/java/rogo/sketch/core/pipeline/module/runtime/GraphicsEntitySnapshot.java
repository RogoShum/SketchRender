package rogo.sketch.core.pipeline.module.runtime;

import rogo.sketch.core.graphics.ecs.GraphicsCapabilityView;
import rogo.sketch.core.graphics.ecs.GraphicsComponentType;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsEntitySchema;
import rogo.sketch.core.graphics.ecs.GraphicsWorld;

import java.util.Objects;
import java.util.Set;

/**
 * Read-only ECS entity snapshot exposed to module runtimes during attach.
 */
public final class GraphicsEntitySnapshot {
    private final GraphicsWorld world;
    private final GraphicsEntityId entityId;
    private final GraphicsEntitySchema schema;

    public GraphicsEntitySnapshot(GraphicsWorld world, GraphicsEntityId entityId) {
        this.world = Objects.requireNonNull(world, "world");
        this.entityId = Objects.requireNonNull(entityId, "entityId");
        this.schema = world.schemaOf(entityId);
    }

    public GraphicsWorld world() {
        return world;
    }

    public GraphicsEntityId entityId() {
        return entityId;
    }

    public GraphicsEntitySchema schema() {
        return schema;
    }

    public GraphicsCapabilityView capabilityView() {
        return schema.capabilityView();
    }

    public Set<GraphicsComponentType<?>> componentTypes() {
        return world.signature(entityId);
    }

    public <T> T component(GraphicsComponentType<T> componentType) {
        return world.component(entityId, componentType);
    }
}
