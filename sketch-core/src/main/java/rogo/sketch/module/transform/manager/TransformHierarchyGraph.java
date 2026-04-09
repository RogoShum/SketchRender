package rogo.sketch.module.transform.manager;

import rogo.sketch.core.api.graphics.Graphics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves parent linkage and hierarchy depth for transform bindings.
 */
public class TransformHierarchyGraph {
    private final List<List<TransformBinding>> syncLayers = new ArrayList<>();
    private final List<List<TransformBinding>> asyncLayers = new ArrayList<>();
    private boolean dirty = true;
    private int syncMaxDepth = 0;
    private int asyncMaxDepth = 0;

    public void markDirty() {
        dirty = true;
    }

    public void resolveIfNeeded(TransformRegistry registry) {
        if (!dirty) {
            return;
        }

        syncLayers.clear();
        asyncLayers.clear();
        syncMaxDepth = 0;
        asyncMaxDepth = 0;

        Map<Integer, Integer> resolvedDepths = new HashMap<>();
        for (TransformBinding binding : registry.activeBindings()) {
            resolveParentAndDepth(binding, registry, new HashSet<>(), resolvedDepths);
        }

        for (TransformBinding binding : registry.activeBindings()) {
            if (binding.updateDomain() == TransformUpdateDomain.ASYNC_TICK) {
                placeBinding(asyncLayers, binding);
                asyncMaxDepth = Math.max(asyncMaxDepth, binding.depth());
            } else {
                placeBinding(syncLayers, binding);
                syncMaxDepth = Math.max(syncMaxDepth, binding.depth());
            }
        }

        dirty = false;
    }

    public List<List<TransformBinding>> syncLayers() {
        return syncLayers;
    }

    public List<List<TransformBinding>> asyncLayers() {
        return asyncLayers;
    }

    public int syncMaxDepth() {
        return syncMaxDepth;
    }

    public int asyncMaxDepth() {
        return asyncMaxDepth;
    }

    private void placeBinding(List<List<TransformBinding>> layers, TransformBinding binding) {
        while (layers.size() <= binding.depth()) {
            layers.add(new ArrayList<>());
        }
        layers.get(binding.depth()).add(binding);
    }

    private int resolveParentAndDepth(
            TransformBinding binding,
            TransformRegistry registry,
            Set<Integer> visiting,
            Map<Integer, Integer> resolvedDepths
    ) {
        Integer cachedDepth = resolvedDepths.get(binding.transformId());
        if (cachedDepth != null) {
            return cachedDepth;
        }

        if (!visiting.add(binding.transformId())) {
            detachParent(binding);
            resolvedDepths.put(binding.transformId(), 0);
            return 0;
        }

        Graphics parentGraphics = binding.parentSource() != null ? binding.parentSource().getTransformParent() : null;
        if (parentGraphics == null) {
            detachParent(binding);
            resolvedDepths.put(binding.transformId(), 0);
            visiting.remove(binding.transformId());
            return 0;
        }

        TransformBinding parentBinding = registry.bindingFor(parentGraphics);
        if (parentBinding == null) {
            detachParent(binding);
            resolvedDepths.put(binding.transformId(), 0);
            visiting.remove(binding.transformId());
            return 0;
        }

        int parentDepth = resolveParentAndDepth(parentBinding, registry, visiting, resolvedDepths);
        binding.setParentTransformId(parentBinding.transformId());
        binding.setDepth(parentDepth + 1);
        visiting.remove(binding.transformId());
        resolvedDepths.put(binding.transformId(), binding.depth());
        return binding.depth();
    }

    private void detachParent(TransformBinding binding) {
        binding.setParentTransformId(-1);
        binding.setDepth(0);
    }
}

