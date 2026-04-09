package rogo.sketch.module.transform.manager;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.util.SharedIdRegistry;
import rogo.sketch.module.transform.TransformParentSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registration and domain grouping for transform-authored graphics.
 */
public class TransformRegistry {
    private final SharedIdRegistry idRegistry = new SharedIdRegistry();
    private final Map<Integer, TransformBinding> bindingsById = new HashMap<>();
    private final IdentityHashMap<Graphics, TransformBinding> bindingsByGraphics = new IdentityHashMap<>();
    private final List<TransformBinding> activeBindings = new ArrayList<>();
    private final List<TransformBinding> syncPipelineBindings = new ArrayList<>();
    private final List<TransformBinding> asyncPipelineBindings = new ArrayList<>();
    private final List<TransformBinding> syncTickBindings = new ArrayList<>();
    private final List<TransformBinding> asyncTickBindings = new ArrayList<>();
    private final List<TransformBinding> frameBindings = new ArrayList<>();

    public TransformBinding registerBinding(Graphics graphics, TransformUpdateDomain updateDomain) {
        if (graphics == null) {
            throw new NullPointerException("graphics");
        }

        TransformBinding existing = bindingsByGraphics.get(graphics);
        if (existing != null) {
            return existing;
        }

        int id = idRegistry.allocate();
        TransformBinding binding = new TransformBinding(
                graphics,
                id,
                updateDomain,
                graphics instanceof TransformParentSource parentSource ? parentSource : null);

        bindingsById.put(id, binding);
        bindingsByGraphics.put(graphics, binding);
        activeBindings.add(binding);

        if (updateDomain == TransformUpdateDomain.ASYNC_TICK) {
            asyncPipelineBindings.add(binding);
            asyncTickBindings.add(binding);
        } else {
            syncPipelineBindings.add(binding);
            if (updateDomain == TransformUpdateDomain.SYNC_TICK) {
                syncTickBindings.add(binding);
            } else if (updateDomain == TransformUpdateDomain.SYNC_FRAME) {
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
        bindingsByGraphics.remove(binding.graphics());
        activeBindings.remove(binding);
        syncPipelineBindings.remove(binding);
        asyncPipelineBindings.remove(binding);
        syncTickBindings.remove(binding);
        asyncTickBindings.remove(binding);
        frameBindings.remove(binding);
        idRegistry.recycle(binding.transformId());
    }

    public TransformBinding bindingFor(Graphics graphics) {
        return bindingsByGraphics.get(graphics);
    }

    public TransformBinding bindingById(int id) {
        return bindingsById.get(id);
    }

    public boolean isRegistered(Graphics graphics) {
        return bindingsByGraphics.containsKey(graphics);
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
        bindingsByGraphics.clear();
        activeBindings.clear();
        syncPipelineBindings.clear();
        asyncPipelineBindings.clear();
        syncTickBindings.clear();
        asyncTickBindings.clear();
        frameBindings.clear();
        idRegistry.clear();
    }
}

