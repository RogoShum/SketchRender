package rogo.sketch.render.pipeline.memory;

import java.nio.ByteBuffer;

/**
 * Interface for allocating memory segments in a linear buffer.
 * Designed for thread-safe, lock-free allocations per frame.
 */
public interface BufferAllocator {
    
    /**
     * Allocates a memory segment of the given size.
     * @param sizeBytes The size in bytes to allocate.
     * @param alignment The alignment requirement (e.g., 4, 16).
     * @return The offset in the buffer where the allocation starts, or -1 if full.
     */
    long allocate(long sizeBytes, int alignment);

    /**
     * Gets the underlying direct ByteBuffer for writing.
     * WARNING: This buffer is shared and accessed concurrently. Write only to your allocated range.
     */
    ByteBuffer getBackingBuffer();

    /**
     * Resets the allocator to the beginning (frame start).
     */
    void reset();
    
    /**
     * Gets the GPU handle (VBO ID) associated with this buffer.
     */
    int getBufferHandle();
}

