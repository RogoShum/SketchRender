package rogo.sketch.core.pipeline;

import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityBlueprint;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

final class PipelineResourceBridge {
    private final Map<KeyId, GraphicsEntityId> installedFunctionResources = new LinkedHashMap<>();
    private final Map<KeyId, GraphicsEntityId> installedDrawCallResources = new LinkedHashMap<>();

    void clearInstalledPipelineResources(Consumer<GraphicsEntityId> destroyEntity) {
        for (GraphicsEntityId entityId : installedFunctionResources.values()) {
            destroyEntity.accept(entityId);
        }
        installedFunctionResources.clear();
        for (GraphicsEntityId entityId : installedDrawCallResources.values()) {
            destroyEntity.accept(entityId);
        }
        installedDrawCallResources.clear();
    }

    void installPipelineResources(
            rogo.sketch.core.resource.GraphicsResourceManager resourceManager,
            Function<GraphicsEntityBlueprint, GraphicsEntityId> spawnEntity) {
        installFunctionResources(resourceManager, spawnEntity);
        installDrawCallResources(resourceManager, spawnEntity);
    }

    private void installFunctionResources(
            rogo.sketch.core.resource.GraphicsResourceManager resourceManager,
            Function<GraphicsEntityBlueprint, GraphicsEntityId> spawnEntity) {
        Map<KeyId, GraphicsEntityBlueprint> resources = resourceManager.getResourcesOfType(ResourceTypes.FUNCTION);
        if (resources.isEmpty()) {
            return;
        }
        List<Map.Entry<KeyId, GraphicsEntityBlueprint>> orderedEntries = new ArrayList<>(resources.entrySet());
        orderedEntries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<KeyId, GraphicsEntityBlueprint> entry : orderedEntries) {
            GraphicsEntityBlueprint blueprint = entry.getValue();
            if (blueprint == null || blueprint.isDisposed()) {
                continue;
            }
            GraphicsEntityId entityId = spawnEntity.apply(blueprint);
            installedFunctionResources.put(entry.getKey(), entityId);
        }
    }

    private void installDrawCallResources(
            rogo.sketch.core.resource.GraphicsResourceManager resourceManager,
            Function<GraphicsEntityBlueprint, GraphicsEntityId> spawnEntity) {
        Map<KeyId, GraphicsEntityBlueprint> resources = resourceManager.getResourcesOfType(ResourceTypes.DRAW_CALL);
        if (resources.isEmpty()) {
            return;
        }
        List<Map.Entry<KeyId, GraphicsEntityBlueprint>> orderedEntries = new ArrayList<>(resources.entrySet());
        orderedEntries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<KeyId, GraphicsEntityBlueprint> entry : orderedEntries) {
            GraphicsEntityBlueprint blueprint = entry.getValue();
            GraphicsBuiltinComponents.StageBindingComponent stageBinding =
                    blueprint != null ? blueprint.component(GraphicsBuiltinComponents.STAGE_BINDING) : null;
            if (blueprint == null || blueprint.isDisposed() || stageBinding == null || stageBinding.renderParameter() == null) {
                continue;
            }
            GraphicsEntityId entityId = spawnEntity.apply(blueprint);
            installedDrawCallResources.put(entry.getKey(), entityId);
        }
    }
}
