package rogo.sketch.core.graphics.ecs;

import rogo.sketch.core.api.ResourceObject;

import java.util.*;

/**
 * Immutable component set used to spawn a graphics entity.
 */
public final class GraphicsEntityBlueprint implements ResourceObject {
    private final Map<GraphicsComponentType<?>, Object> components;
    private boolean disposed;

    private GraphicsEntityBlueprint(Map<GraphicsComponentType<?>, Object> components) {
        this.components = Collections.unmodifiableMap(new LinkedHashMap<>(components));
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean has(GraphicsComponentType<?> componentType) {
        return components.containsKey(componentType);
    }

    public Set<GraphicsComponentType<?>> componentTypes() {
        return Set.copyOf(components.keySet());
    }

    public Map<GraphicsComponentType<?>, Object> components() {
        return components;
    }

    public <T> T component(GraphicsComponentType<T> componentType) {
        return componentType.cast(components.get(componentType));
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    public static final class Builder {
        private final Map<GraphicsComponentType<?>, Object> components = new LinkedHashMap<>();

        public <T> Builder put(GraphicsComponentType<T> componentType, T value) {
            Objects.requireNonNull(componentType, "componentType");
            Objects.requireNonNull(value, "value");
            components.put(componentType, value);
            return this;
        }

        public Builder remove(GraphicsComponentType<?> componentType) {
            components.remove(componentType);
            return this;
        }

        public GraphicsEntityBlueprint build() {
            return new GraphicsEntityBlueprint(components);
        }
    }
}
