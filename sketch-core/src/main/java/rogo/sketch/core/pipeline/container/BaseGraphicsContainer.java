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

public abstract class BaseGraphicsContainer<C extends RenderContext> implements VisibilityIndexContainer<C> {
    protected final Collection<Tickable> tickableInstances = new LinkedHashSet<>();
    protected final Collection<AsyncTickable> asyncTickableInstances = new LinkedHashSet<>();

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
        // Formal v2 stage flows reconcile dirty state; base containers no longer orchestrate it here.
    }
}

