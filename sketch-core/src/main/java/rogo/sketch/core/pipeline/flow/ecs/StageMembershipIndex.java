package rogo.sketch.core.pipeline.flow.ecs;

import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsQuery;
import rogo.sketch.core.graphics.ecs.GraphicsWorld;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.StageRouteDescriptor;
import rogo.sketch.core.util.KeyId;

import java.util.*;

/**
 * Tracks which ECS entities belong to each stage/pipeline pair.
 */
public final class StageMembershipIndex {
    private final Map<StagePipelineKey, LinkedHashSet<GraphicsEntityId>> members = new LinkedHashMap<>();
    private final Map<GraphicsEntityId, LinkedHashSet<StagePipelineKey>> reverse = new LinkedHashMap<>();
    private long version = 0L;

    public synchronized void register(GraphicsEntityId entityId, GraphicsBuiltinComponents.StageBindingComponent binding) {
        register(entityId, binding, null);
    }

    public synchronized void register(
            GraphicsEntityId entityId,
            GraphicsBuiltinComponents.StageBindingComponent binding,
            GraphicsBuiltinComponents.StageRoutesComponent stageRoutes) {
        if (entityId == null || !entityId.isValid()) {
            return;
        }
        unregister(entityId);
        LinkedHashSet<StagePipelineKey> registeredKeys = new LinkedHashSet<>();
        if (stageRoutes != null) {
            for (StageRouteDescriptor route : stageRoutes.routes()) {
                if (route == null || !route.enabled()) {
                    continue;
                }
                StagePipelineKey key = new StagePipelineKey(route.stageId(), route.pipelineType());
                members.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(entityId);
                registeredKeys.add(key);
            }
        } else if (binding != null) {
            StagePipelineKey key = new StagePipelineKey(binding.stageId(), binding.pipelineType());
            members.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(entityId);
            registeredKeys.add(key);
        }
        if (registeredKeys.isEmpty()) {
            return;
        }
        reverse.put(entityId, registeredKeys);
        version++;
    }

    public synchronized void unregister(GraphicsEntityId entityId) {
        LinkedHashSet<StagePipelineKey> keys = reverse.remove(entityId);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (StagePipelineKey key : keys) {
            LinkedHashSet<GraphicsEntityId> entities = members.get(key);
            if (entities == null) {
                continue;
            }
            entities.remove(entityId);
            if (entities.isEmpty()) {
                members.remove(key);
            }
        }
        version++;
    }

    public synchronized List<GraphicsEntityId> entities(KeyId stageId, PipelineType pipelineType) {
        LinkedHashSet<GraphicsEntityId> entities = members.get(new StagePipelineKey(stageId, pipelineType));
        return entities == null ? List.of() : List.copyOf(entities);
    }

    public synchronized void rebuild(GraphicsWorld world) {
        members.clear();
        reverse.clear();
        version++;
        if (world == null) {
            return;
        }
        for (GraphicsEntityId entityId : world.query(GraphicsQuery.builder().build())) {
            GraphicsBuiltinComponents.StageBindingComponent binding =
                    world.component(entityId, GraphicsBuiltinComponents.STAGE_BINDING);
            GraphicsBuiltinComponents.StageRoutesComponent stageRoutes =
                    world.component(entityId, GraphicsBuiltinComponents.STAGE_ROUTES);
            register(entityId, binding, stageRoutes);
        }
    }

    public synchronized void clear() {
        members.clear();
        reverse.clear();
        version++;
    }

    public synchronized long version() {
        return version;
    }

    private record StagePipelineKey(KeyId stageId, PipelineType pipelineType) {
    }
}
