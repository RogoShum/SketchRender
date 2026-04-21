package rogo.sketch.core.graphics.ecs;

import rogo.sketch.core.util.KeyId;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable schema snapshot for a graphics ECS entity.
 */
public record GraphicsEntitySchema(
        GraphicsEntityId entityId,
        Set<KeyId> componentIds,
        GraphicsCapabilityView capabilityView,
        List<GraphicsCapabilityDescriptor> capabilities,
        List<GraphicsAuthoringDescriptor> authoringDescriptors
) {
    public static GraphicsEntitySchema from(GraphicsEntityId entityId, Set<GraphicsComponentType<?>> signature) {
        Set<KeyId> componentIds = new LinkedHashSet<>();
        for (GraphicsComponentType<?> componentType : signature) {
            componentIds.add(componentType.id());
        }
        GraphicsCapabilityView capabilityView = GraphicsCapabilityResolver.resolve(signature);
        return new GraphicsEntitySchema(
                entityId,
                Set.copyOf(componentIds),
                capabilityView,
                capabilityView.capabilities(),
                capabilityView.authoringDescriptors());
    }
}
