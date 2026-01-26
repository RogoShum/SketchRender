package rogo.sketch.core.pipeline.container;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

import java.util.*;

/**
 * Simple queue-based graphics container using LinkedHashMap.
 * This is the default container type with no spatial optimizations.
 * All instances are considered visible.
 */
public class QueueContainer<C extends RenderContext> implements GraphicsContainer<C> {
    private final Map<KeyId, Graphics> instances = new LinkedHashMap<>();
    private final Collection<Graphics> tickableInstances = new ArrayList<>();

    @Override
    public void add(Graphics graphics) {
        if (!instances.containsKey(graphics.getIdentifier())) {
            instances.put(graphics.getIdentifier(), graphics);
            if (graphics.tickable()) {
                tickableInstances.add(graphics);
            }
        } else {
            Graphics old = instances.get(graphics.getIdentifier());
            if (old.shouldDiscard()) {
                remove(graphics.getIdentifier());
                instances.put(graphics.getIdentifier(), graphics);
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
        if (removed != null && removed.tickable()) {
            tickableInstances.remove(removed);
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
        // Use a list to avoid ConcurrentModificationException during iteration
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
        return new ArrayList<>(instances.values());
    }

    @Override
    public Collection<Graphics> getVisibleInstances(C context) {
        Collection<Graphics> visible = new ArrayList<>();
        for (Graphics graphics : instances.values()) {
            if (graphics.shouldRender()) {
                visible.add(graphics);
            }
        }
        return visible;
    }

    @Override
    public void clear() {
        instances.clear();
    }

    @Override
    public int size() {
        return instances.size();
    }

    @Override
    public boolean isEmpty() {
        return instances.isEmpty();
    }
}
