package rogo.sketch.render.data.filler;

import org.lwjgl.system.MemoryUtil;
import rogo.sketch.render.data.format.DataFormat;

/**
 * Data filler implementation that writes directly to memory addresses
 * Uses MemoryWriteStrategy for implementation
 */
public class MemoryFiller extends DirectDataFiller {
    private boolean needsFreeing;

    public MemoryFiller(DataFormat format, long memoryAddress, long capacity) {
        super(format, new MemoryWriteStrategy(memoryAddress, capacity));
        this.needsFreeing = false;
    }

    /**
     * Get the base memory address
     */
    public long getBaseAddress() {
        return ((MemoryWriteStrategy) writeStrategy).getBaseAddress();
    }

    /**
     * Zero out the entire memory region
     */
    public MemoryFiller clear() {
        ((MemoryWriteStrategy) writeStrategy).clear();
        return this;
    }

    /**
     * Copy data from another memory region
     */
    public MemoryFiller copyFrom(long sourceAddress, long size) {
        if (size > getCapacity()) {
            throw new IllegalArgumentException("Source size exceeds capacity");
        }
        MemoryUtil.memCopy(sourceAddress, getBaseAddress(), size);
        return this;
    }

    /**
     * Copy data to another memory region
     */
    public MemoryFiller copyTo(long destAddress, long size) {
        if (size > getCapacity()) {
            throw new IllegalArgumentException("Copy size exceeds capacity");
        }
        MemoryUtil.memCopy(getBaseAddress(), destAddress, size);
        return this;
    }

    /**
     * Free the memory if it was allocated by this filler
     */
    public void dispose() {
        if (needsFreeing && getBaseAddress() != MemoryUtil.NULL) {
            MemoryUtil.nmemFree(getBaseAddress());
            needsFreeing = false;
        }
    }

    /**
     * Create a new MemoryFiller with allocated memory
     */
    public static MemoryFiller allocate(DataFormat format, int vertexCount) {
        long capacity = (long) format.getStride() * vertexCount;
        long address = MemoryUtil.nmemCalloc(vertexCount, format.getStride());
        if (address == MemoryUtil.NULL) {
            throw new OutOfMemoryError("Failed to allocate memory for vertex data");
        }
        
        MemoryFiller filler = new MemoryFiller(format, address, capacity);
        filler.needsFreeing = true;
        return filler;
    }

    /**
     * Create a new MemoryFiller wrapping an existing memory address
     */
    public static MemoryFiller wrap(DataFormat format, long memoryAddress, long capacity) {
        return new MemoryFiller(format, memoryAddress, capacity);
    }

    /**
     * Create a new MemoryFiller for a specific vertex count at an address
     */
    public static MemoryFiller wrap(DataFormat format, long memoryAddress, int vertexCount) {
        long capacity = (long) format.getStride() * vertexCount;
        return new MemoryFiller(format, memoryAddress, capacity);
    }
}