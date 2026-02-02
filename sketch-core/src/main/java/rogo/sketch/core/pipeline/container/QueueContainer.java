package rogo.sketch.core.pipeline.container;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

import java.util.*;

public class QueueContainer<C extends RenderContext> extends BaseGraphicsContainer<C> {
    public static final KeyId CONTAINER_TYPE = KeyId.of("queue");

    private final Map<KeyId, Graphics> instances = new LinkedHashMap<>();

    @Override
    protected boolean addImpl(Graphics graphics) {
        if (!instances.containsKey(graphics.getIdentifier())) {
            instances.put(graphics.getIdentifier(), graphics);
            return true;
        } else {
            Graphics old = instances.get(graphics.getIdentifier());
            if (old.shouldDiscard()) {
                remove(graphics.getIdentifier()); // Calls removeImpl
                instances.put(graphics.getIdentifier(), graphics);
                return true;
            } else {
                System.err.println("Duplicate graphics instance: " + graphics.getIdentifier());
                return false;
            }
        }
    }

    @Override
    protected Graphics removeImpl(KeyId identifier) {
        return instances.remove(identifier);
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
        tickableInstances.clear();
        asyncTickableInstances.clear();
    }

    @Override
    public int size() {
        return instances.size();
    }

    @Override
    public boolean isEmpty() {
        return instances.isEmpty();
    }

    @Override
    public KeyId getContainerType() {
        return CONTAINER_TYPE;
    }
}