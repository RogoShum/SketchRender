package rogo.sketch.render.data.filler;

import org.lwjgl.system.MemoryUtil;

/**
 * WriteStrategy implementation for direct memory access
 */
public class MemoryWriteStrategy implements WriteStrategy {
    private final long baseAddress;
    private final long capacity;
    private long currentPosition;

    public MemoryWriteStrategy(long memoryAddress, long capacity) {
        this.baseAddress = memoryAddress;
        this.capacity = capacity;
        this.currentPosition = 0;
    }

    @Override
    public void writeFloat(float value) {
        checkBounds(currentPosition, Float.BYTES);
        MemoryUtil.memPutFloat(baseAddress + currentPosition, value);
        currentPosition += Float.BYTES;
    }

    @Override
    public void writeInt(int value) {
        checkBounds(currentPosition, Integer.BYTES);
        MemoryUtil.memPutInt(baseAddress + currentPosition, value);
        currentPosition += Integer.BYTES;
    }

    @Override
    public void writeByte(byte value) {
        checkBounds(currentPosition, Byte.BYTES);
        MemoryUtil.memPutByte(baseAddress + currentPosition, value);
        currentPosition += Byte.BYTES;
    }

    @Override
    public void writeShort(short value) {
        checkBounds(currentPosition, Short.BYTES);
        MemoryUtil.memPutShort(baseAddress + currentPosition, value);
        currentPosition += Short.BYTES;
    }

    @Override
    public void writeDouble(double value) {
        checkBounds(currentPosition, Double.BYTES);
        MemoryUtil.memPutDouble(baseAddress + currentPosition, value);
        currentPosition += Double.BYTES;
    }

    @Override
    public void writeFloatAt(long byteOffset, float value) {
        checkBounds(byteOffset, Float.BYTES);
        MemoryUtil.memPutFloat(baseAddress + byteOffset, value);
    }

    @Override
    public void writeIntAt(long byteOffset, int value) {
        checkBounds(byteOffset, Integer.BYTES);
        MemoryUtil.memPutInt(baseAddress + byteOffset, value);
    }

    @Override
    public void writeByteAt(long byteOffset, byte value) {
        checkBounds(byteOffset, Byte.BYTES);
        MemoryUtil.memPutByte(baseAddress + byteOffset, value);
    }

    @Override
    public void writeShortAt(long byteOffset, short value) {
        checkBounds(byteOffset, Short.BYTES);
        MemoryUtil.memPutShort(baseAddress + byteOffset, value);
    }

    @Override
    public void writeDoubleAt(long byteOffset, double value) {
        checkBounds(byteOffset, Double.BYTES);
        MemoryUtil.memPutDouble(baseAddress + byteOffset, value);
    }

    @Override
    public long getCurrentPosition() {
        return currentPosition;
    }

    @Override
    public void setPosition(long position) {
        if (position < 0 || position > capacity) {
            throw new IndexOutOfBoundsException("Position out of bounds: " + position);
        }
        this.currentPosition = position;
    }

    @Override
    public void advance(int bytes) {
        setPosition(currentPosition + bytes);
    }

    @Override
    public void finish() {
        // Nothing to do for memory strategy
    }

    @Override
    public void reset() {
        currentPosition = 0;
    }

    @Override
    public boolean supportsRandomAccess() {
        return true;
    }

    @Override
    public boolean supportsPositionTracking() {
        return true;
    }

    @Override
    public long getCapacity() {
        return capacity;
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
     * Clear the entire memory region
     */
    public void clear() {
        MemoryUtil.memSet(baseAddress, 0, capacity);
        reset();
    }
}
