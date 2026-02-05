package rogo.sketch.core.transform;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.api.graphics.TransformableGraphics;
import rogo.sketch.core.pipeline.flow.impl.ContainerListener;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

/**
 * Interface for managing transforms in the pipeline.
 * Implemented by MatrixManager in sketch-mod.
 * 
 * This interface serves as a bridge between sketch-core (no GL) and sketch-mod (with GL).
 */
public interface TransformManager extends ContainerListener {
    
    /**
     * Register a transform with the manager.
     * 
     * @param transform The transform to register
     * @return The assigned ID
     */
    int registerTransform(Transform transform);
    
    /**
     * Unregister a transform from the manager.
     * 
     * @param transform The transform to unregister
     */
    void unregisterTransform(Transform transform);
    
    /**
     * Get a registered transform by ID.
     * 
     * @param id The transform ID
     * @return The transform, or null if not found
     */
    Transform getTransform(int id);
    
    /**
     * Check if a transform is registered.
     * 
     * @param transform The transform to check
     * @return true if registered
     */
    boolean isRegistered(Transform transform);

    /**
     * Swap data for all registered transforms.
     */
    void swapTransformData();
    
    /**
     * Get the number of active transforms.
     */
    int getActiveCount();
    
    // ===== ContainerListener Implementation =====
    
    @Override
    default void onInstanceAdded(Graphics graphics, RenderParameter renderParameter, KeyId containerType) {
        if (graphics instanceof TransformableGraphics tg && tg.hasTransform()) {
            Transform transform = tg.getTransform();
            if (!isRegistered(transform)) {
                registerTransform(transform);
            }
        }
    }
    
    @Override
    default void onInstanceRemoved(Graphics graphics) {
        if (graphics instanceof TransformableGraphics tg && tg.hasTransform()) {
            Transform transform = tg.getTransform();
            if (isRegistered(transform)) {
                unregisterTransform(transform);
            }
        }
    }
    
    @Override
    default void onInstanceDirty(Graphics graphics) {
        // Transform dirty state is handled internally
    }
}