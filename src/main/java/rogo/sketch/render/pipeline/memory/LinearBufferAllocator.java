package rogo.sketch.render.pipeline.memory;

import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.util.GLFeatureChecker;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple linear allocator backed by a fixed-size ByteBuffer and an OpenGL Buffer.
 * Uses AtomicLong for thread-safe allocation.
 */
public class LinearBufferAllocator implements BufferAllocator {

    private final int bufferHandle;
    private final long capacity;
    private final ByteBuffer backingBuffer;
    private final AtomicLong currentOffset = new AtomicLong(0);
    private final int usage; // GL_DYNAMIC_DRAW, etc.

    public LinearBufferAllocator(long capacityBytes, int usage) {
        this.capacity = capacityBytes;
        this.usage = usage;
        
        // Allocate off-heap memory
        this.backingBuffer = MemoryUtil.memAlloc((int) capacity);
        
        // Create GL Buffer
        if (GLFeatureChecker.supportsDSA()) {
            this.bufferHandle = GL45.glCreateBuffers();
            // Allocate immutable storage if possible for performance, or standard mutable
            GL45.glNamedBufferData(this.bufferHandle, capacity, usage);
        } else {
            // Fallback (Simplified, assumes GL context is bound properly when needed)
            // Real implementation might need to defer creation
            this.bufferHandle = -1; 
            throw new UnsupportedOperationException("DSA required for this implementation currently");
        }
    }

    @Override
    public long allocate(long sizeBytes, int alignment) {
        long current, next, alignedCurrent;
        do {
            current = currentOffset.get();
            // Align current offset
            alignedCurrent = (current + (alignment - 1)) & ~(alignment - 1);
            next = alignedCurrent + sizeBytes;
            
            if (next > capacity) {
                return -1; // Out of memory
            }
        } while (!currentOffset.compareAndSet(current, next));
        
        return alignedCurrent;
    }

    @Override
    public ByteBuffer getBackingBuffer() {
        return backingBuffer;
    }

    @Override
    public void reset() {
        currentOffset.set(0);
        // We don't clear the ByteBuffer memory, just the pointer.
    }

    @Override
    public int getBufferHandle() {
        return bufferHandle;
    }
    
    /**
     * Uploads the currently used portion of the buffer to the GPU.
     * Should be called after all allocations and writes are done for the frame.
     */
    public void upload() {
        long usedSize = currentOffset.get();
        if (usedSize == 0) return;

        // Limit the buffer to what was used
        ByteBuffer slice = backingBuffer.slice(0, (int) usedSize);
        
        // Upload (Orphan logic could be added here for pipelining)
        GL45.glNamedBufferSubData(bufferHandle, 0, slice);
    }
    
    public void dispose() {
        MemoryUtil.memFree(backingBuffer);
        GL45.glDeleteBuffers(bufferHandle);
    }
}

