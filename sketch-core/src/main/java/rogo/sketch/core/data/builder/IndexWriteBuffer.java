package rogo.sketch.core.data.builder;

import org.lwjgl.system.MemoryUtil;

/**
 * Dedicated index writer. Keeps index writes separate from vertex record state.
 */
public class IndexWriteBuffer extends NativeWriteBuffer {
    public IndexWriteBuffer() {
        this(4L);
    }

    public IndexWriteBuffer(long capacity) {
        super(MemoryUtil.nmemAlloc(capacity), capacity, false);
    }

    public IndexWriteBuffer(long address, long capacity, boolean externalMemory) {
        super(address, capacity, externalMemory);
    }

    public IndexWriteBuffer putIndex(int index) {
        put(index);
        return this;
    }

    public IndexWriteBuffer putIndexAt(int indexPosition, int index) {
        putIntAt((long) indexPosition * Integer.BYTES, index);
        return this;
    }

    public int getIndexCount() {
        return Math.toIntExact(getWriteOffset() / Integer.BYTES);
    }

    public ParallelWritableSlice sliceIndices(int startIndex, int indexCount) {
        return sliceView((long) startIndex * Integer.BYTES, (long) indexCount * Integer.BYTES);
    }
}

