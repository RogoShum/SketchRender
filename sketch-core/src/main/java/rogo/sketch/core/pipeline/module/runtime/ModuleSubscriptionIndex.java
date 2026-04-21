package rogo.sketch.core.pipeline.module.runtime;

import rogo.sketch.core.graphics.ecs.GraphicsEntityId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks module entity subscriptions and active entity bindings.
 */
public final class ModuleSubscriptionIndex {
    private final Map<String, List<GraphicsEntitySubscription>> subscriptionsByModule = new LinkedHashMap<>();
    private final Map<GraphicsEntityId, Set<BindingRecord>> bindingsByEntity = new LinkedHashMap<>();

    public void replaceSubscriptions(String moduleId, List<GraphicsEntitySubscription> subscriptions) {
        if (moduleId == null) {
            return;
        }
        subscriptionsByModule.put(moduleId, subscriptions != null ? List.copyOf(subscriptions) : List.of());
    }

    public List<GraphicsEntitySubscription> subscriptions(String moduleId) {
        return subscriptionsByModule.getOrDefault(moduleId, List.of());
    }

    public Collection<String> moduleIds() {
        return Collections.unmodifiableCollection(subscriptionsByModule.keySet());
    }

    public boolean isBound(GraphicsEntityId entityId, String moduleId, String subscriptionId) {
        Set<BindingRecord> bindings = bindingsByEntity.get(entityId);
        return bindings != null && bindings.contains(new BindingRecord(moduleId, subscriptionId));
    }

    public boolean hasAnyBinding(GraphicsEntityId entityId, String moduleId) {
        Set<BindingRecord> bindings = bindingsByEntity.get(entityId);
        if (bindings == null || bindings.isEmpty() || moduleId == null) {
            return false;
        }
        for (BindingRecord binding : bindings) {
            if (moduleId.equals(binding.moduleId())) {
                return true;
            }
        }
        return false;
    }

    public void bind(GraphicsEntityId entityId, String moduleId, String subscriptionId) {
        bindingsByEntity
                .computeIfAbsent(entityId, ignored -> new LinkedHashSet<>())
                .add(new BindingRecord(moduleId, subscriptionId));
    }

    public void unbind(GraphicsEntityId entityId, String moduleId, String subscriptionId) {
        Set<BindingRecord> bindings = bindingsByEntity.get(entityId);
        if (bindings == null) {
            return;
        }
        bindings.remove(new BindingRecord(moduleId, subscriptionId));
        if (bindings.isEmpty()) {
            bindingsByEntity.remove(entityId);
        }
    }

    public List<BindingRecord> bindingsForEntity(GraphicsEntityId entityId) {
        Set<BindingRecord> bindings = bindingsByEntity.get(entityId);
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        return List.copyOf(bindings);
    }

    public void clearBindingsForEntity(GraphicsEntityId entityId) {
        if (entityId != null) {
            bindingsByEntity.remove(entityId);
        }
    }

    public List<GraphicsEntityId> entitiesForModule(String moduleId) {
        if (moduleId == null) {
            return List.of();
        }
        List<GraphicsEntityId> entityIds = new ArrayList<>();
        for (Map.Entry<GraphicsEntityId, Set<BindingRecord>> entry : bindingsByEntity.entrySet()) {
            for (BindingRecord binding : entry.getValue()) {
                if (moduleId.equals(binding.moduleId())) {
                    entityIds.add(entry.getKey());
                    break;
                }
            }
        }
        return List.copyOf(entityIds);
    }

    public void clearBindingsForModule(String moduleId) {
        if (moduleId == null) {
            return;
        }
        bindingsByEntity.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(binding -> moduleId.equals(binding.moduleId()));
            return entry.getValue().isEmpty();
        });
    }

    public void clear() {
        subscriptionsByModule.clear();
        bindingsByEntity.clear();
    }

    public record BindingRecord(
            String moduleId,
            String subscriptionId
    ) {
    }
}
