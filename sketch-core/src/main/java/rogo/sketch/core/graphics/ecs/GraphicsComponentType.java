package rogo.sketch.core.graphics.ecs;

import rogo.sketch.core.util.KeyId;

import java.util.Objects;

/**
 * Typed component descriptor for graphics ECS storage.
 */
public final class GraphicsComponentType<T> implements Comparable<GraphicsComponentType<?>> {
    private final KeyId id;
    private final Class<T> valueType;

    private GraphicsComponentType(KeyId id, Class<T> valueType) {
        this.id = Objects.requireNonNull(id, "id");
        this.valueType = Objects.requireNonNull(valueType, "valueType");
    }

    public static <T> GraphicsComponentType<T> of(KeyId id, Class<T> valueType) {
        return new GraphicsComponentType<>(id, valueType);
    }

    public KeyId id() {
        return id;
    }

    public Class<T> valueType() {
        return valueType;
    }

    public T cast(Object value) {
        return valueType.cast(value);
    }

    @Override
    public int compareTo(GraphicsComponentType<?> other) {
        return id.compareTo(other.id);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof GraphicsComponentType<?> other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "GraphicsComponentType[" + id + "]";
    }
}
