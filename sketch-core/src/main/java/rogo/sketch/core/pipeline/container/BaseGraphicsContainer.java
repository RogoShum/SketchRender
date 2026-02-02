package rogo.sketch.core.pipeline.container;

import rogo.sketch.core.api.graphics.AsyncTickable;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.api.graphics.Tickable;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public abstract class BaseGraphicsContainer<C extends RenderContext> implements GraphicsContainer<C> {
    protected final Collection<Tickable> tickableInstances = new LinkedHashSet<>();
    protected final Collection<AsyncTickable> asyncTickableInstances = new LinkedHashSet<>();
    protected final List<ContainerListener> listeners = new ArrayList<>();

    @Override
    public void add(Graphics graphics) {
        add(graphics, null);
    }

    @Override
    public void add(Graphics graphics, RenderParameter renderParameter) {
        if (addImpl(graphics)) {
            if (graphics instanceof Tickable t) {
                tickableInstances.add(t);
            }
            if (graphics instanceof AsyncTickable at) {
                asyncTickableInstances.add(at);
            }
            notifyInstanceAdded(graphics, renderParameter);
        }
    }

    // Subclasses implement this. Return true if added (or replaced).
    protected abstract boolean addImpl(Graphics graphics);

    @Override
    public void remove(KeyId identifier) {
        Graphics removed = removeImpl(identifier);
        if (removed != null) {
            if (removed instanceof Tickable t) {
                tickableInstances.remove(t);
            }
            if (removed instanceof AsyncTickable at) {
                asyncTickableInstances.remove(at);
            }
            notifyInstanceRemoved(removed);
        }
    }

    protected abstract Graphics removeImpl(KeyId identifier);

    @Override
    public void tick(C context) {
        for (Tickable tickable : tickableInstances) {
            tickable.tick();
        }
    }

    @Override
    public void asyncTick(C context) {
        for (AsyncTickable asyncTickable : asyncTickableInstances) {
            asyncTickable.asyncTick();
        }
        cleanupDiscarded();
    }

    protected void cleanupDiscarded() {
        List<KeyId> toRemove = new ArrayList<>();
        for (Graphics g : getAllInstances()) {
            if (g.shouldDiscard()) {
                toRemove.add(g.getIdentifier());
            }
        }
        for (KeyId id : toRemove) {
            remove(id);
        }
    }

    @Override
    public void swapData() {
        for (AsyncTickable asyncTickable : asyncTickableInstances) {
            asyncTickable.swapData();
        }
    }

    @Override
    public void dirtyCheck() {
        for (Tickable tickable : tickableInstances) {
            if (tickable instanceof Graphics g && g.isBatchDirty()) {
                notifyInstanceDirty(g);
                g.clearBatchDirtyFlags();
            }
        }
        // Also check AsyncTickables? They might be dirty too.
        for (AsyncTickable asyncTickable : asyncTickableInstances) {
            if (asyncTickable instanceof Graphics g && g.isBatchDirty()) {
                notifyInstanceDirty(g);
                g.clearBatchDirtyFlags();
            }
        }
    }

    // ===== Listener Support =====

    @Override
    public void addListener(ContainerListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(ContainerListener listener) {
        listeners.remove(listener);
    }

    protected void notifyInstanceAdded(Graphics graphics, RenderParameter renderParameter) {
        for (ContainerListener listener : listeners) {
            listener.onInstanceAdded(graphics, renderParameter, getContainerType());
        }
    }

    protected void notifyInstanceRemoved(Graphics graphics) {
        for (ContainerListener listener : listeners) {
            listener.onInstanceRemoved(graphics);
        }
    }

    protected void notifyInstanceDirty(Graphics graphics) {
        for (ContainerListener listener : listeners) {
            listener.onInstanceDirty(graphics);
        }
    }
}
