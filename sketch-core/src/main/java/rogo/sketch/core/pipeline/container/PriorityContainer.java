package rogo.sketch.core.pipeline.container;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

import java.util.*;

/**
 * Graphics container that maintains instances sorted by their natural ordering
 * (usually priority).
 * Useful for FunctionGraphics where execution order matters.
 */
public class PriorityContainer<C extends RenderContext> implements GraphicsContainer<C> {
    private final Map<KeyId, Graphics> instances = new LinkedHashMap<>();
    private final Collection<Graphics> tickableInstances = new LinkedHashSet<>();
    private final List<Graphics> sortedInstances = new ArrayList<>();
    private boolean dirty = false;

    @Override
    public void add(Graphics graphics) {
        if (!instances.containsKey(graphics.getIdentifier())) {
            instances.put(graphics.getIdentifier(), graphics);
            sortedInstances.add(graphics);
            dirty = true;

            if (graphics.tickable()) {
                tickableInstances.add(graphics);
            }
        } else {
            Graphics old = instances.get(graphics.getIdentifier());
            if (old.shouldDiscard()) {
                remove(graphics.getIdentifier());
                instances.put(graphics.getIdentifier(), graphics);
                sortedInstances.add(graphics);
                dirty = true;

                if (graphics.tickable()) {
                    tickableInstances.add(graphics);
                }
            } else {
                System.err.println("Duplicate graphics instance: " + graphics.getIdentifier());
            }
        }
    }

    @Override
    public void remove(KeyId identifier) {
        Graphics removed = instances.remove(identifier);
        if (removed != null) {
            sortedInstances.remove(removed);
            if (removed.tickable()) {
                tickableInstances.remove(removed);
            }
            // Removing from sortedInstances doesn't break relative order,
            // but we keep dirty = true if we want to be safe, though
            // ArrayList.remove() is O(n). For PriorityContainer,
            // we might want a better structure for sortedInstances if n is large.
        }
    }

    @Override
    public void tick(C context) {
        for (Graphics graphics : tickableInstances) {
            if (graphics.shouldTick()) {
                graphics.tick(context);
            }
        }

        // Cleanup discarded instances
        instances.values().removeIf(graphics -> {
            if (graphics.shouldDiscard()) {
                sortedInstances.remove(graphics);
                if (graphics.tickable()) {
                    tickableInstances.remove(graphics);
                }
                return true;
            }
            return false;
        });
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
    }
}