package rogo.sketch.core.api;

/**
 * Base interface for all managed resources.
 * Resources that need a GPU handle should implement GpuObject instead.
 */
public interface ResourceObject {
    /**
     * Release all resources held by this object.
     */
    void dispose();

    /**
     * Check if this resource has been disposed.
     * @return true if disposed
     */
    boolean isDisposed();
}