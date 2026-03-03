package rogo.sketch.core.pipeline.container;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.Collection;
import java.util.List;

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
     * Add a graphics instance with render parameter context.
     */
    default void add(Graphics graphics, RenderParameter renderParameter) {
        add(graphics);
    }

    /**
     * Remove a graphics instance by identifier.
     */
    void remove(KeyId identifier);

    /**
     * Tick all instances in the container.
     */
    void tick(C context);

    /**
     * Async tick all instances in the container.
     */
    void asyncTick(C context);

    void dirtyCheck();

    void swapData();

    /**
     * Get all instances regardless of visibility.
     */
    Collection<Graphics> getAllInstances();

    /**
     * Get instances that are visible from the current camera frustum.
     * Implementations may use frustum culling for optimization.
     *
     * @param context Render context containing frustum information
     * @return List of visible graphics instances
     */
    List<Graphics> getVisibleInstances(C context);

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
    
    /**
     * Get the container type identifier.
     *
     * @return The container type KeyId
     */
    KeyId getContainerType();
}