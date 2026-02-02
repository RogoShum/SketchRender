package rogo.sketch.core.pipeline.container;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

import java.util.*;

public class PriorityContainer<C extends RenderContext> extends BaseGraphicsContainer<C> {
    public static final KeyId CONTAINER_TYPE = KeyId.of("priority");

    private final Map<KeyId, Graphics> instances = new LinkedHashMap<>();
    private final List<Graphics> sortedInstances = new ArrayList<>();
    private boolean dirty = false;

    @Override
    protected boolean addImpl(Graphics graphics) {
        if (!instances.containsKey(graphics.getIdentifier())) {
            instances.put(graphics.getIdentifier(), graphics);
            sortedInstances.add(graphics);
            dirty = true;
            return true;
        } else {
            Graphics old = instances.get(graphics.getIdentifier());
            if (old.shouldDiscard()) {
                remove(graphics.getIdentifier());
                instances.put(graphics.getIdentifier(), graphics);
                sortedInstances.add(graphics);
                dirty = true;
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    protected Graphics removeImpl(KeyId identifier) {
        Graphics removed = instances.remove(identifier);
        if (removed != null) {
            sortedInstances.remove(removed);
            dirty = true; // Optimization: maybe no need to resort if just removing?
            // But simpler to ensure sortedness
        }
        return removed;
    }

    @Override
    public void asyncTick(C context) {
        super.asyncTick(context);
        // Note: sortedInstances list cleanup is done via removeImpl called by
        // super.cleanupDiscarded().
        // Does AsyncTick modify sortedInstances?
        // super.cleanupDiscarded() which I moved to swapData() will do it.
        // So safe.
    }

    @Override
    public Collection<Graphics> getAllInstances() {
        if (dirty)
            ensureSorted();
        return new ArrayList<>(sortedInstances);
    }

    @Override
    public Collection<Graphics> getVisibleInstances(C context) {
        if (dirty)
            ensureSorted();
        List<Graphics> visible = new ArrayList<>();
        for (Graphics graphics : sortedInstances) {
            if (graphics.shouldRender()) {
                visible.add(graphics);
            }
        }
        return visible;
    }

    private void ensureSorted() {
        Collections.sort(sortedInstances, (a, b) -> {
            if (a instanceof Comparable && b instanceof Comparable) {
                @SuppressWarnings("unchecked")
                Comparable<Graphics> ca = (Comparable<Graphics>) a;
                return ca.compareTo(b);
            }
            return 0;
        });
        dirty = false;
    }

    @Override
    public void clear() {
        instances.clear();
        sortedInstances.clear();
        tickableInstances.clear();
        asyncTickableInstances.clear();
    }

    @Override
    public KeyId getContainerType() {
        return CONTAINER_TYPE;
    }
}