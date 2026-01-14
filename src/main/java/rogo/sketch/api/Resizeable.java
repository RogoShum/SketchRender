package rogo.sketch.api;

/**
 * Interface for resources that can be resized.
 */
public interface Resizeable {
    /**
     * Resizes the resource to at least the specified capacity.
     * @param newCapacity The minimum required capacity in bytes.
     * @return The new memory address of the resource (if applicable), or 0/null semantics if managed internally.
     */
    long resize(long newCapacity);
}

