package rogo.sketch.core.pool;

/**
 * Interface for objects that can be pooled and reused
 */
public interface Poolable {
    /**
     * Reset the object to its initial state for reuse
     */
    void reset();
}