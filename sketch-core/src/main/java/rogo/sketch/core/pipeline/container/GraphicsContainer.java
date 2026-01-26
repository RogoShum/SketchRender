package rogo.sketch.core.pipeline.container;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

import java.util.Collection;

/**
 * Container for graphics instances with different storage and culling
 * strategies.
 * 
 * @param <C> Render context type
 */
public interface GraphicsContainer<C extends RenderContext> {
    /**
     * Add a graphics instance to the container.
     */
    void add(Graphics graphics);

    /**
     * Remove a graphics instance by identifier.
     */
    void remove(KeyId identifier);

    /**
     * Tick all instances in the container.
     */
    void tick(C context);

    /**
     * Get all instances regardless of visibility.
     */
    Collection<Graphics> getAllInstances();

    /**
     * Get instances that are visible from the current camera frustum.
     * Implementations may use frustum culling for optimization.
     * 
     * @param context Render context containing frustum information
     * @return Collection of visible graphics instances
     */
    Collection<Graphics> getVisibleInstances(C context);

    /**
     * Clear all instances from the container.
     */
    void clear();

    /**
     * Get the number of instances in the container.
     */
    default int size() {
        return getAllInstances().size();
    }

    /**
     * Check if the container is empty.
     */
    default boolean isEmpty() {
        return size() == 0;
    }
}
