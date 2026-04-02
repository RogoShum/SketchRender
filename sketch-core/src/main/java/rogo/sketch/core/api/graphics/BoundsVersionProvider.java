package rogo.sketch.core.api.graphics;

/**
 * Optional graphics-side version hook for visibility metadata invalidation.
 */
public interface BoundsVersionProvider {
    long boundsVersion();
}
