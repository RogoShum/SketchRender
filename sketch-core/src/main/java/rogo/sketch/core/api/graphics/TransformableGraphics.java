package rogo.sketch.core.api.graphics;

import rogo.sketch.core.transform.Transform;

/**
 * Interface for Graphics instances that support the Transform system.
 * Graphics implementing this interface will have their transforms automatically
 * registered with the MatrixManager when added to a container.
 */
public interface TransformableGraphics extends Graphics {
    
    /**
     * Get the Transform associated with this graphics instance.
     * 
     * @return The Transform, or null if not available
     */
    Transform getTransform();
    
    /**
     * Check if this graphics instance has a valid transform.
     * 
     * @return true if getTransform() returns a non-null value
     */
    default boolean hasTransform() {
        return getTransform() != null;
    }
}

