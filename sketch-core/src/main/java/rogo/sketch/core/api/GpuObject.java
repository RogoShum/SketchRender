package rogo.sketch.core.api;

/**
 * Interface for GPU resources that have a native handle (texture, buffer, shader, etc.)
 * Extends ResourceObject with handle management.
 */
public interface GpuObject extends ResourceObject {
    /**
     * Get the native GPU handle (OpenGL ID, Vulkan handle, etc.)
     * @return The native handle, or 0 if not allocated
     */
    int getHandle();
}

