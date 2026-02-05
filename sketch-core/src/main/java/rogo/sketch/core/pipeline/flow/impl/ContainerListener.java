package rogo.sketch.core.pipeline.flow.impl;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

/**
 * Listener for container events to synchronize with BatchContainer.
 * <p>
 * BatchContainer implementations should implement this interface to receive
 * notifications when graphics instances are added, removed, or become dirty.
 * </p>
 */
public interface ContainerListener {
    
    /**
     * Called when a graphics instance is added to the container.
     *
     * @param graphics The graphics instance being added
     * @param renderParameter The render parameter for this instance
     * @param containerType The type of container (queue, octree, etc.)
     */
    void onInstanceAdded(Graphics graphics, RenderParameter renderParameter, KeyId containerType);
    
    /**
     * Called when a graphics instance is removed from the container.
     *
     * @param graphics The graphics instance being removed
     */
    void onInstanceRemoved(Graphics graphics);
    
    /**
     * Called when a graphics instance becomes dirty (renderSetting or mesh changed).
     * This is typically triggered by the graphics instance itself.
     *
     * @param graphics The graphics instance that became dirty
     */
    void onInstanceDirty(Graphics graphics);
}