package rogo.sketch.core.graphics.ecs;

import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Derives editor-facing capability views from ECS component signatures.
 */
public final class GraphicsCapabilityResolver {
    private GraphicsCapabilityResolver() {
    }

    public static GraphicsCapabilityView resolve(Set<GraphicsComponentType<?>> signature) {
        Set<KeyId> componentIds = new LinkedHashSet<>();
        for (GraphicsComponentType<?> componentType : signature) {
            componentIds.add(componentType.id());
        }
        List<GraphicsCapabilityDescriptor> capabilities = new ArrayList<>();
        List<GraphicsAuthoringDescriptor> authoringDescriptors = new ArrayList<>();
        GraphicsUpdateDomain updateDomain = inferUpdateDomain(signature);

        registerCapability(capabilities, authoringDescriptors, signature,
                GraphicsBuiltinComponents.RASTER_RENDERABLE,
                GraphicsCapabilityId.of("raster_renderable"),
                true, false, false,
                false, false, false, false, false,
                updateDomain,
                true);
        registerCapability(capabilities, authoringDescriptors, signature,
                GraphicsBuiltinComponents.COMPUTE_DISPATCH,
                GraphicsCapabilityId.of("compute_dispatch"),
                false, true, false,
                false, false, false, false, false,
                updateDomain,
                true);
        registerCapability(capabilities, authoringDescriptors, signature,
                GraphicsBuiltinComponents.FUNCTION_INVOKE,
                GraphicsCapabilityId.of("function_invoke"),
                false, false, true,
                false, false, false, false, false,
                GraphicsUpdateDomain.SYNC_FRAME,
                true);
        registerCapability(capabilities, authoringDescriptors, signature,
                GraphicsBuiltinComponents.BOUNDS,
                GraphicsCapabilityId.of("bounds"),
                false, false, false,
                true, false, false, false, false,
                updateDomain,
                true);
        registerCapability(capabilities, authoringDescriptors, signature,
                GraphicsBuiltinComponents.TRANSFORM_BINDING,
                GraphicsCapabilityId.of("transform_binding"),
                false, false, false,
                false, true, false, false, false,
                updateDomain,
                true);
        registerCapability(capabilities, authoringDescriptors, signature,
                GraphicsBuiltinComponents.INSTANCE_VERTEX_AUTHORING,
                GraphicsCapabilityId.of("instance_vertex_authoring"),
                false, false, false,
                false, false, true, false, false,
                updateDomain,
                true);
        registerCapability(capabilities, authoringDescriptors, signature,
                GraphicsBuiltinComponents.UNIFORM_AUTHORING,
                GraphicsCapabilityId.of("uniform_authoring"),
                false, false, false,
                false, false, false, true, false,
                updateDomain,
                true);
        registerCapability(capabilities, authoringDescriptors, signature,
                GraphicsBuiltinComponents.SSBO_AUTHORING,
                GraphicsCapabilityId.of("ssbo_authoring"),
                false, false, false,
                false, false, false, false, true,
                updateDomain,
                true);
        return new GraphicsCapabilityView(
                Set.copyOf(componentIds),
                List.copyOf(capabilities),
                List.copyOf(authoringDescriptors));
    }

    private static void registerCapability(
            List<GraphicsCapabilityDescriptor> capabilities,
            List<GraphicsAuthoringDescriptor> authoringDescriptors,
            Set<GraphicsComponentType<?>> signature,
            GraphicsComponentType<?> componentType,
            GraphicsCapabilityId capabilityId,
            boolean raster,
            boolean compute,
            boolean function,
            boolean bounds,
            boolean transform,
            boolean instance,
            boolean uniform,
            boolean ssbo,
            GraphicsUpdateDomain updateDomain,
            boolean editorExposed) {
        if (!signature.contains(componentType)) {
            return;
        }
        GraphicsAuthoringDescriptor authoringDescriptor = new GraphicsAuthoringDescriptor(
                capabilityId,
                componentType.id(),
                bounds,
                transform,
                instance,
                uniform,
                ssbo);
        capabilities.add(new GraphicsCapabilityDescriptor(
                capabilityId,
                componentType.id(),
                raster,
                compute,
                function,
                updateDomain,
                editorExposed,
                authoringDescriptor));
        authoringDescriptors.add(authoringDescriptor);
    }

    private static GraphicsUpdateDomain inferUpdateDomain(Set<GraphicsComponentType<?>> signature) {
        if (signature.contains(GraphicsBuiltinComponents.ASYNC_TICK_DRIVER)) {
            return GraphicsUpdateDomain.ASYNC_TICK;
        }
        if (signature.contains(GraphicsBuiltinComponents.TICK_DRIVER)) {
            return GraphicsUpdateDomain.SYNC_TICK;
        }
        if (signature.contains(GraphicsBuiltinComponents.COMPUTE_DISPATCH)
                || signature.contains(GraphicsBuiltinComponents.FUNCTION_INVOKE)) {
            return GraphicsUpdateDomain.SYNC_FRAME;
        }
        return GraphicsUpdateDomain.STATIC;
    }
}
