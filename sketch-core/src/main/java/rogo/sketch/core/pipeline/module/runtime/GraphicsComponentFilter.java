package rogo.sketch.core.pipeline.module.runtime;

import rogo.sketch.core.graphics.ecs.GraphicsComponentType;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Required/excluded component filter for module entity subscriptions.
 */
public final class GraphicsComponentFilter {
    private final Set<GraphicsComponentType<?>> required;
    private final Set<GraphicsComponentType<?>> excluded;

    private GraphicsComponentFilter(Set<GraphicsComponentType<?>> required, Set<GraphicsComponentType<?>> excluded) {
        this.required = Collections.unmodifiableSet(new TreeSet<>(required));
        this.excluded = Collections.unmodifiableSet(new TreeSet<>(excluded));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Set<GraphicsComponentType<?>> required() {
        return required;
    }

    public Set<GraphicsComponentType<?>> excluded() {
        return excluded;
    }

    public boolean matches(Set<GraphicsComponentType<?>> signature) {
        return signature != null && signature.containsAll(required) && Collections.disjoint(signature, excluded);
    }

    public static final class Builder {
        private final Set<GraphicsComponentType<?>> required = new LinkedHashSet<>();
        private final Set<GraphicsComponentType<?>> excluded = new LinkedHashSet<>();

        public Builder require(GraphicsComponentType<?> componentType) {
            required.add(Objects.requireNonNull(componentType, "componentType"));
            return this;
        }

        public Builder exclude(GraphicsComponentType<?> componentType) {
            excluded.add(Objects.requireNonNull(componentType, "componentType"));
            return this;
        }

        public GraphicsComponentFilter build() {
            return new GraphicsComponentFilter(required, excluded);
        }
    }
}
