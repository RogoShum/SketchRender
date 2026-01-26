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
    private final Collection<Graphics> tickableInstances = new ArrayList<>();
    private final List<Graphics> sortedInstances = new ArrayList<>();

    @Override
    public void add(Graphics graphics) {
        if (!instances.containsKey(graphics.getIdentifier())) {
            instances.put(graphics.getIdentifier(), graphics);
            sortedInstances.add(graphics);
            ensureSorted();

            if (graphics.tickable()) {
                tickableInstances.add(graphics);
            }
        } else {
            Graphics old = instances.get(graphics.getIdentifier());
            if (old.shouldDiscard()) {
                remove(graphics.getIdentifier());
                instances.put(graphics.getIdentifier(), graphics);
                sortedInstances.add(graphics);
                ensureSorted();

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
        }
    }

    @Override
    public void tick(C context) {
        for (Graphics graphics : tickableInstances) {
            if (graphics.shouldTick()) {
                graphics.tick(context);
            }
        }

        List<KeyId> toRemove = new ArrayList<>();
        for (Graphics graphics : instances.values()) {
            if (graphics.shouldDiscard()) {
                toRemove.add(graphics.getIdentifier());
            }
        }

        for (KeyId id : toRemove) {
            remove(id);
        }
    }

    @Override
    public Collection<Graphics> getAllInstances() {
        return new ArrayList<>(sortedInstances);
    }

    @Override
    public Collection<Graphics> getVisibleInstances(C context) {
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
    }

    @Override
    public void clear() {
        instances.clear();
        sortedInstances.clear();
        tickableInstances.clear();
    }
}