package rogo.sketch.core.graphics.ecs;

import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class GraphicsUniformSubject {
    @FunctionalInterface
    public interface ComponentResolver {
        Object resolve(GraphicsComponentType<?> componentType);
    }

    private final GraphicsEntityId entityId;
    private final GraphicsBuiltinComponents.IdentityComponent identity;
    private final GraphicsBuiltinComponents.ResourceOriginComponent resourceOrigin;
    private final GraphicsBuiltinComponents.GraphicsTagsComponent tags;
    private final GraphicsEntitySchema schema;
    private final KeyId stageId;
    private final PipelineType pipelineType;
    private final RenderParameter renderParameter;
    private final ComponentResolver componentResolver;
    private final Supplier<Map<GraphicsComponentType<?>, Object>> componentsSupplier;
    private volatile Map<GraphicsComponentType<?>, Object> components;

    public GraphicsUniformSubject(
            GraphicsEntityId entityId,
            GraphicsBuiltinComponents.IdentityComponent identity,
            GraphicsBuiltinComponents.ResourceOriginComponent resourceOrigin,
            GraphicsBuiltinComponents.GraphicsTagsComponent tags,
            GraphicsEntitySchema schema,
            KeyId stageId,
            PipelineType pipelineType,
            RenderParameter renderParameter,
            Map<GraphicsComponentType<?>, Object> components) {
        this(
                entityId,
                identity,
                resourceOrigin,
                tags,
                schema,
                stageId,
                pipelineType,
                renderParameter,
                normalizeComponents(components)::get,
                () -> normalizeComponents(components));
    }

    public GraphicsUniformSubject(
            GraphicsEntityId entityId,
            GraphicsBuiltinComponents.IdentityComponent identity,
            GraphicsBuiltinComponents.ResourceOriginComponent resourceOrigin,
            GraphicsBuiltinComponents.GraphicsTagsComponent tags,
            GraphicsEntitySchema schema,
            KeyId stageId,
            PipelineType pipelineType,
            RenderParameter renderParameter,
            ComponentResolver componentResolver,
            Supplier<Map<GraphicsComponentType<?>, Object>> componentsSupplier) {
        this.entityId = Objects.requireNonNull(entityId, "entityId");
        this.identity = identity;
        this.resourceOrigin = resourceOrigin;
        this.tags = tags != null ? tags : new GraphicsBuiltinComponents.GraphicsTagsComponent(Set.of());
        this.schema = Objects.requireNonNull(schema, "schema");
        this.stageId = stageId;
        this.pipelineType = pipelineType;
        this.renderParameter = renderParameter;
        this.componentResolver = componentResolver;
        this.componentsSupplier = componentsSupplier;
        this.components = null;
    }

    private static Map<GraphicsComponentType<?>, Object> normalizeComponents(Map<GraphicsComponentType<?>, Object> components) {
        return components == null || components.isEmpty() ? Map.of() : components;
    }

    public GraphicsEntityId entityId() {
        return entityId;
    }

    public GraphicsBuiltinComponents.IdentityComponent identity() {
        return identity;
    }

    public KeyId identifier() {
        return identity != null ? identity.identifier() : KeyId.of("sketch", "unknown_entity");
    }

    public GraphicsBuiltinComponents.ResourceOriginComponent resourceOrigin() {
        return resourceOrigin;
    }

    public GraphicsBuiltinComponents.GraphicsTagsComponent tags() {
        return tags;
    }

    public boolean hasTag(KeyId tag) {
        return tags.hasTag(tag);
    }

    public GraphicsEntitySchema schema() {
        return schema;
    }

    public GraphicsCapabilityView capabilityView() {
        return schema.capabilityView();
    }

    public KeyId stageId() {
        return stageId;
    }

    public PipelineType pipelineType() {
        return pipelineType;
    }

    public RenderParameter renderParameter() {
        return renderParameter;
    }

    public Map<GraphicsComponentType<?>, Object> components() {
        Map<GraphicsComponentType<?>, Object> snapshot = components;
        if (snapshot != null) {
            return snapshot;
        }
        Supplier<Map<GraphicsComponentType<?>, Object>> supplier = componentsSupplier;
        if (supplier == null) {
            snapshot = Map.of();
        } else {
            Map<GraphicsComponentType<?>, Object> supplied = supplier.get();
            snapshot = supplied == null || supplied.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(supplied));
        }
        components = snapshot;
        return snapshot;
    }

    public <T> T component(GraphicsComponentType<T> componentType) {
        if (componentType == null) {
            return null;
        }
        ComponentResolver resolver = componentResolver;
        if (resolver != null) {
            return componentType.cast(resolver.resolve(componentType));
        }
        return componentType.cast(components().get(componentType));
    }
}
