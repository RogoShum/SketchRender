package rogo.sketch.core.pipeline.module;

import rogo.sketch.core.api.graphics.Graphics;

import java.util.*;

/**
 * Tracks which {@link GraphicsModule}s have claimed each graphics instance.
 * <p>
 * Replaces the old global container listener dispatch with an explicit
 * per-instance binding table.
 * </p>
 */
public final class ModuleBindingIndex {

    // Graphics identity -> set of modules that claimed it
    private final Map<Graphics, Set<GraphicsModule>> bindings = new IdentityHashMap<>();

    /**
     * Record that the given module has claimed the given graphics.
     */
    public void bind(Graphics graphics, GraphicsModule module) {
        bindings.computeIfAbsent(graphics, g -> Collections.newSetFromMap(new IdentityHashMap<>()))
                .add(module);
    }

    /**
     * Get all modules bound to a graphics instance.
     */
    public Set<GraphicsModule> getModules(Graphics graphics) {
        Set<GraphicsModule> set = bindings.get(graphics);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }

    /**
     * Remove all bindings for a graphics instance and return the modules that were bound.
     */
    public Set<GraphicsModule> unbindAll(Graphics graphics) {
        Set<GraphicsModule> removed = bindings.remove(graphics);
        return removed != null ? removed : Collections.emptySet();
    }

    /**
     * Check if any module has claimed this graphics.
     */
    public boolean isBound(Graphics graphics) {
        Set<GraphicsModule> set = bindings.get(graphics);
        return set != null && !set.isEmpty();
    }

    /**
     * Clear all bindings.
     */
    public void clear() {
        bindings.clear();
    }
}

