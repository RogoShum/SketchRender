package rogo.sketch.core.object;

import rogo.sketch.core.graphics.ecs.GraphicsComponentType;

/**
 * Conflict-aware component writer used by object factories and augmentors.
 */
public interface ObjectGraphicsBlueprintWriter {
    <T> void put(GraphicsComponentType<T> componentType, T value);

    void remove(GraphicsComponentType<?> componentType);

    boolean has(GraphicsComponentType<?> componentType);

    <T> T component(GraphicsComponentType<T> componentType);
}
