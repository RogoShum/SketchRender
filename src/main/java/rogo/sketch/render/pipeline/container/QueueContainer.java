package rogo.sketch.render.pipeline.container;

import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.util.KeyId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple queue-based graphics container using LinkedHashMap.
 * This is the default container type with no spatial optimizations.
 * All instances are considered visible.
 */
public class QueueContainer<C extends RenderContext> implements GraphicsContainer<C> {
    private final Map<KeyId, Graphics> instances = new LinkedHashMap<>();

    @Override
    public void add(Graphics graphics) {
        instances.put(graphics.getIdentifier(), graphics);
    }

    @Override
    public void remove(KeyId identifier) {
        instances.remove(identifier);
    }

    @Override
    public void tick(C context) {
        for (Graphics graphics : instances.values()) {
            if (graphics.shouldTick()) {
                graphics.tick(context);
            }
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
