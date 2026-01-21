package rogo.sketch.feature.culling.aabb;

import org.joml.primitives.AABBf;

/**
 * Interface for objects that have an axis-aligned bounding box.
 * Uses JOML primitives for cross-platform compatibility.
 */
public interface AABBObject {
    /**
     * Get the axis-aligned bounding box for this object in world space.
     * 
     * @return AABB or null if no bounds
     */
    AABBf getAABB();
}