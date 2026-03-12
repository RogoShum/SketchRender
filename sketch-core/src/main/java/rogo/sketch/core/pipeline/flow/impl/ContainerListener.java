package rogo.sketch.core.pipeline.flow.impl;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

/**
 * Listener for instance lifecycle events emitted by BatchContainer.
 * <p>
 * This interface is used by systems such as TransformManager to track graphics
 * registration changes without coupling to concrete container internals.
 * </p>
 *
 * @deprecated Use {@link rogo.sketch.core.pipeline.module.GraphicsModule} instead.
 *             The module system provides explicit attachment/detachment without
 *             coupling to container internals.
 */
@Deprecated
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
}