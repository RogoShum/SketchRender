package rogo.sketch.core.graphics.ecs;

import java.util.Objects;

/**
 * Entry point for creating and destroying graphics ECS entities.
 */
public final class GraphicsEntityAssembler {
    private final GraphicsWorld world;

    public GraphicsEntityAssembler(GraphicsWorld world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    public GraphicsEntityId spawn(GraphicsEntityBlueprint blueprint) {
        return world.spawn(blueprint);
    }

    public void destroy(GraphicsEntityId entityId) {
        world.destroy(entityId);
    }

    public GraphicsWorld world() {
        return world;
    }
}
