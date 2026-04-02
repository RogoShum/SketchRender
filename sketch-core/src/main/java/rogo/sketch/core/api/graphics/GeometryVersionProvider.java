package rogo.sketch.core.api.graphics;

/**
 * Optional graphics-side version hook for geometry trait invalidation.
 */
public interface GeometryVersionProvider {
    long geometryVersion();
}
