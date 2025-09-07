package rogo.sketch.render.data.filler;

import org.lwjgl.system.MemoryUtil;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.data.format.DataElement;

/**
 * Data filler implementation that writes directly to memory addresses
 */
public class MemoryFiller extends DataFiller {
    private final long baseAddress;
    private final long capacity;
    private boolean needsFreeing;

    public MemoryFiller(DataFormat format, long memoryAddress, long capacity) {
        super(format);
        this.baseAddress = memoryAddress;
        this.capacity = capacity;
        this.needsFreeing = false;
    }

    @Override
    public void writeFloat(float value) {
        long offset = getCurrentOffset();
        checkBounds(offset, Float.BYTES);
        MemoryUtil.memPutFloat(baseAddress + offset, value);
    }

    @Override
    public void writeInt(int value) {
        long offset = getCurrentOffset();
        checkBounds(offset, Integer.BYTES);
        MemoryUtil.memPutInt(baseAddress + offset, value);
    }

    @Override
    public void writeUInt(int value) {
        writeInt(value); // Same as signed int in memory
    }

    @Override
    public void writeByte(byte value) {
        long offset = getCurrentOffset();
        checkBounds(offset, Byte.BYTES);
        MemoryUtil.memPutByte(baseAddress + offset, value);
    }

    @Override
    public void writeUByte(byte value) {
        writeByte(value); // Same as signed byte in memory
    }

    @Override
    public void writeShort(short value) {
        long offset = getCurrentOffset();
        checkBounds(offset, Short.BYTES);
        MemoryUtil.memPutShort(baseAddress + offset, value);
    }

    @Override
    public void writeUShort(short value) {
        writeShort(value); // Same as signed short in memory
    }

    @Override
    public void writeDouble(double value) {
        long offset = getCurrentOffset();
        checkBounds(offset, Double.BYTES);
        MemoryUtil.memPutDouble(baseAddress + offset, value);
    }

    @Override
    public void writeFloatAt(int vertexIndex, int elementIndex, float value) {
        long offset = calculateBytePosition(vertexIndex, elementIndex);
        checkBounds(offset, Float.BYTES);
        MemoryUtil.memPutFloat(baseAddress + offset, value);
    }

    @Override
    public void writeIntAt(int vertexIndex, int elementIndex, int value) {
        long offset = calculateBytePosition(vertexIndex, elementIndex);
        checkBounds(offset, Integer.BYTES);
        MemoryUtil.memPutInt(baseAddress + offset, value);
    }

    @Override
    public void writeUIntAt(int vertexIndex, int elementIndex, int value) {
        writeIntAt(vertexIndex, elementIndex, value); // Same as signed int in memory
    }

    @Override
    public void writeByteAt(int vertexIndex, int elementIndex, byte value) {
        long offset = calculateBytePosition(vertexIndex, elementIndex);
        checkBounds(offset, Byte.BYTES);
        MemoryUtil.memPutByte(baseAddress + offset, value);
    }

    @Override
    public void writeUByteAt(int vertexIndex, int elementIndex, byte value) {
        writeByteAt(vertexIndex, elementIndex, value); // Same as signed byte in memory
    }

    @Override
    public void writeShortAt(int vertexIndex, int elementIndex, short value) {
        long offset = calculateBytePosition(vertexIndex, elementIndex);
        checkBounds(offset, Short.BYTES);
        MemoryUtil.memPutShort(baseAddress + offset, value);
    }

    @Override
    public void writeUShortAt(int vertexIndex, int elementIndex, short value) {
        writeShortAt(vertexIndex, elementIndex, value); // Same as signed short in memory
    }

    @Override
    public void writeDoubleAt(int vertexIndex, int elementIndex, double value) {
        long offset = calculateBytePosition(vertexIndex, elementIndex);
        checkBounds(offset, Double.BYTES);
        MemoryUtil.memPutDouble(baseAddress + offset, value);
    }

    private long getCurrentOffset() {
        long vertexOffset = currentVertex * format.getStride();
        if (currentElementIndex < format.getElementCount()) {
            DataElement element = format.getElement(currentElementIndex);
            return vertexOffset + element.getOffset();
        }
        return vertexOffset;
    }

    private void checkBounds(long offset, int size) {
        if (offset + size > capacity) {
            throw new IndexOutOfBoundsException(
                String.format("Memory write would exceed bounds: offset=%d, size=%d, capacity=%d", 
                            offset, size, capacity));
        }
    }

    /**
     * Get the base memory address
     */
    public long getBaseAddress() {
        return baseAddress;
    }

    /**
     * Get the capacity in bytes
     */
    public long getCapacity() {
        return capacity;
    }

    /**
     * Zero out the entire memory region
     */
    public MemoryFiller clear() {
        MemoryUtil.memSet(baseAddress, 0, capacity);
        currentVertex = 0;
        currentElementIndex = 0;
        return this;
    }

    /**
     * Copy data from another memory region
     */
    public MemoryFiller copyFrom(long sourceAddress, long size) {
        if (size > capacity) {
            throw new IllegalArgumentException("Source size exceeds capacity");
        }
        MemoryUtil.memCopy(sourceAddress, baseAddress, size);
        return this;
    }

    /**
     * Copy data to another memory region
     */
    public MemoryFiller copyTo(long destAddress, long size) {
        if (size > capacity) {
            throw new IllegalArgumentException("Copy size exceeds capacity");
        }
        MemoryUtil.memCopy(baseAddress, destAddress, size);
        return this;
    }

    /**
     * Free the memory if it was allocated by this filler
     */
    public void dispose() {
        if (needsFreeing && baseAddress != MemoryUtil.NULL) {
            MemoryUtil.nmemFree(baseAddress);
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
    
    @Override
    public void end() {
        // For MemoryFiller, no special finalization needed
        // Data is already written to memory
    }
}