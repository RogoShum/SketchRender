package rogo.sketch.core.graphics.ecs;

/**
 * Stable entity identity inside the graphics ECS world.
 */
public record GraphicsEntityId(int slot, int generation) {
    public static final GraphicsEntityId INVALID = new GraphicsEntityId(-1, -1);

    public boolean isValid() {
        return slot >= 0 && generation >= 0;
    }
}
