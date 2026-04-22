package rogo.sketch.module.transform.manager;

import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsUpdateDomain;
import rogo.sketch.core.util.SharedIdRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registration and domain grouping for transform-authored ECS graphics entities.
 */
public class TransformRegistry {
    private final SharedIdRegistry idRegistry = new SharedIdRegistry();
    private final Map<Integer, TransformBinding> bindingsById = new HashMap<>();
    private final Map<GraphicsEntityId, TransformBinding> bindingsByEntity = new LinkedHashMap<>();
    private final List<TransformBinding> activeBindings = new ArrayList<>();
    private final List<TransformBinding> syncPipelineBindings = new ArrayList<>();
    private final List<TransformBinding> asyncPipelineBindings = new ArrayList<>();
    private final List<TransformBinding> syncTickBindings = new ArrayList<>();
    private final List<TransformBinding> asyncTickBindings = new ArrayList<>();
    private final List<TransformBinding> frameBindings = new ArrayList<>();

    public TransformBinding registerBinding(
            GraphicsEntityId entityId,
            GraphicsBuiltinComponents.TransformBindingComponent bindingComponent,
            GraphicsBuiltinComponents.TransformHierarchyComponent hierarchyComponent) {
        if (entityId == null) {
            throw new NullPointerException("entityId");
        }
        if (bindingComponent == null || bindingComponent.updateDomain() == null) {
            throw new IllegalArgumentException("bindingComponent");
        }

        TransformBinding existing = bindingsByEntity.get(entityId);
        if (existing != null) {
            return existing;
        }

        int id = idRegistry.allocate();
        TransformBinding binding = new TransformBinding(
                entityId,
                id,
                bindingComponent.updateDomain(),
                bindingComponent,
                hierarchyComponent);

        bindingsById.put(id, binding);
        bindingsByEntity.put(entityId, binding);
        activeBindings.add(binding);

        if (binding.updateDomain() == GraphicsUpdateDomain.ASYNC_TICK) {
            asyncPipelineBindings.add(binding);
            asyncTickBindings.add(binding);
        } else {
            syncPipelineBindings.add(binding);
            if (binding.updateDomain() == GraphicsUpdateDomain.SYNC_TICK) {
                syncTickBindings.add(binding);
            } else if (binding.updateDomain() == GraphicsUpdateDomain.SYNC_FRAME) {
                frameBindings.add(binding);
            }
        }

        return binding;
    }

    public void unregisterBinding(TransformBinding binding) {
        if (binding == null) {
            return;
        }

        bindingsById.remove(binding.transformId());
        bindingsByEntity.remove(binding.entityId());
        activeBindings.remove(binding);
        syncPipelineBindings.remove(binding);
        asyncPipelineBindings.remove(binding);
        syncTickBindings.remove(binding);
        asyncTickBindings.remove(binding);
        frameBindings.remove(binding);
        idRegistry.recycle(binding.transformId());
    }

    public TransformBinding bindingFor(GraphicsEntityId entityId) {
        return bindingsByEntity.get(entityId);
    }

    public TransformBinding bindingById(int id) {
        return bindingsById.get(id);
    }

    public boolean isRegistered(GraphicsEntityId entityId) {
        return bindingsByEntity.containsKey(entityId);
    }

    public int activeCount() {
        return activeBindings.size();
    }

    public int maxAllocatedId() {
        return idRegistry.getMaxId();
    }

    public Collection<TransformBinding> activeBindings() {
        return activeBindings;
    }

    public List<TransformBinding> syncPipelineBindings() {
        return syncPipelineBindings;
    }

    public List<TransformBinding> asyncPipelineBindings() {
        return asyncPipelineBindings;
    }

    public List<TransformBinding> syncTickBindings() {
        return syncTickBindings;
    }

    public List<TransformBinding> asyncTickBindings() {
        return asyncTickBindings;
    }

    public List<TransformBinding> frameBindings() {
        return frameBindings;
    }

    public void clear() {
        bindingsById.clear();
        bindingsByEntity.clear();
        activeBindings.clear();
        syncPipelineBindings.clear();
        asyncPipelineBindings.clear();
        syncTickBindings.clear();
        asyncTickBindings.clear();
        frameBindings.clear();
        idRegistry.clear();
    }
}
