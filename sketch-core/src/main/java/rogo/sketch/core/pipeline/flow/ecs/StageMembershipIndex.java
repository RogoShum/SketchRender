package rogo.sketch.core.pipeline.flow.ecs;

import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsWorld;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.util.KeyId;

import java.util.*;

/**
 * Tracks which ECS entities belong to each stage/pipeline pair.
 */
public final class StageMembershipIndex {
    private final Map<StagePipelineKey, LinkedHashSet<GraphicsEntityId>> members = new LinkedHashMap<>();
    private final Map<GraphicsEntityId, StagePipelineKey> reverse = new LinkedHashMap<>();
    private long version = 0L;

    public synchronized void register(GraphicsEntityId entityId, GraphicsBuiltinComponents.StageBindingComponent binding) {
        if (entityId == null || !entityId.isValid() || binding == null) {
            return;
        }
        unregister(entityId);
        StagePipelineKey key = new StagePipelineKey(binding.stageId(), binding.pipelineType());
        members.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(entityId);
        reverse.put(entityId, key);
        version++;
    }

    public synchronized void unregister(GraphicsEntityId entityId) {
        StagePipelineKey key = reverse.remove(entityId);
        if (key == null) {
            return;
        }
        LinkedHashSet<GraphicsEntityId> entities = members.get(key);
        if (entities == null) {
            return;
        }
        entities.remove(entityId);
        if (entities.isEmpty()) {
            members.remove(key);
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
        for (GraphicsEntityId entityId : world.query(rogo.sketch.core.graphics.ecs.GraphicsQuery.builder()
                .require(GraphicsBuiltinComponents.STAGE_BINDING)
                .build())) {
            GraphicsBuiltinComponents.StageBindingComponent binding =
                    world.component(entityId, GraphicsBuiltinComponents.STAGE_BINDING);
            register(entityId, binding);
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
