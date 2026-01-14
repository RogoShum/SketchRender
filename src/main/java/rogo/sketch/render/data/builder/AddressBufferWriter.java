package rogo.sketch.render.data.builder;

import org.lwjgl.system.MemoryUtil;
import rogo.sketch.api.DataResourceObject;

import java.nio.ByteBuffer;

/**
 * A writer that writes directly to absolute memory addresses (Unsafe).
 * Ideal for Mapped Buffers, SSBO pointers, or off-heap memory.
 */
public class AddressBufferWriter implements DataBufferWriter {
    private long startAddress;
    private long currentAddress;
    private long endAddress;
    private long capacity;

    private ResizeCallback resizeCallback;

    public AddressBufferWriter(long address, long capacity) {
        setAddress(address, capacity);
    }

    public AddressBufferWriter(DataResourceObject dataResource) {
        this.setAddress(dataResource.getMemoryAddress(), dataResource.getCapacity());
        if (dataResource instanceof rogo.sketch.api.Resizeable resizeable) {
            this.setResizeCallback(resizeable::resize);
        }
    }

    public void setAddress(long address, long capacity) {
        this.startAddress = address;
        this.currentAddress = address;
        this.capacity = capacity;
        this.endAddress = address + capacity;
    }

    public void setResizeCallback(ResizeCallback callback) {
        this.resizeCallback = callback;
    }

    @Override
    public void ensureCapacity(int additionalBytes) {
        if (currentAddress + additionalBytes > endAddress) {
            if (resizeCallback != null) {
                // Calculate required new size (e.g., current usage + needed + padding/growth factor)
                long used = currentAddress - startAddress;
                long required = used + additionalBytes;
                long newCapacity = Math.max(capacity * 2, required);

                // Request resize, expect new pointer back
                long newAddress = resizeCallback.resize(newCapacity);

                // Update internal state
                this.startAddress = newAddress;
                this.currentAddress = newAddress + used; // Restore relative position
                this.capacity = newCapacity;
                this.endAddress = newAddress + newCapacity;
            } else {
                throw new IndexOutOfBoundsException("AddressBufferWriter out of bounds and no resize callback set.");
            }
        }
    }

    @Override
    public void advance(int bytes) {
        ensureCapacity(bytes);
        currentAddress += bytes;
    }

    @Override
    public long getWriteOffset() {
        return currentAddress - startAddress;
    }

    @Override
    public void setWriteOffset(long offset) {
        ensureCapacity((int)(offset - getWriteOffset()));
        currentAddress = startAddress + offset;
    }

    @Override
    public boolean supportsRandomAccess() {
        return true;
    }

    @Override
    public void putFloatAt(long byteOffset, float value) {
        MemoryUtil.memPutFloat(startAddress + byteOffset, value);
    }

    @Override
    public void putIntAt(long byteOffset, int value) {
        MemoryUtil.memPutInt(startAddress + byteOffset, value);
    }

    @Override
    public void putByteAt(long byteOffset, byte value) {
        MemoryUtil.memPutByte(startAddress + byteOffset, value);
    }

    @Override
    public void putShortAt(long byteOffset, short value) {
        MemoryUtil.memPutShort(startAddress + byteOffset, value);
    }

    @Override
    public void putDoubleAt(long byteOffset, double value) {
        MemoryUtil.memPutDouble(startAddress + byteOffset, value);
    }

    @Override
    public DataBufferWriter putFloat(float value) {
        ensureCapacity(4);
        MemoryUtil.memPutFloat(currentAddress, value);
        currentAddress += 4;
        return this;
    }

    @Override
    public DataBufferWriter putInt(int value) {
        ensureCapacity(4);
        MemoryUtil.memPutInt(currentAddress, value);
        currentAddress += 4;
        return this;
    }

    @Override
    public DataBufferWriter putUInt(int value) {
        return putInt(value);
    }

    @Override
    public DataBufferWriter putByte(byte value) {
        ensureCapacity(1);
        MemoryUtil.memPutByte(currentAddress, value);
        currentAddress += 1;
        return this;
    }

    @Override
    public DataBufferWriter putUByte(byte value) {
        return putByte(value);
    }

    @Override
    public DataBufferWriter putShort(short value) {
        ensureCapacity(2);
        MemoryUtil.memPutShort(currentAddress, value);
        currentAddress += 2;
        return this;
    }

    @Override
    public DataBufferWriter putUShort(short value) {
        return putShort(value);
    }

    @Override
    public DataBufferWriter putDouble(double value) {
        ensureCapacity(8);
        MemoryUtil.memPutDouble(currentAddress, value);
        currentAddress += 8;
        return this;
    }

    @Override
    public DataBufferWriter putLong(long value) {
        ensureCapacity(8);
        MemoryUtil.memPutLong(currentAddress, value);
        currentAddress += 8;
        return this;
    }

    @Override
    public DataBufferWriter put(ByteBuffer buffer) {
        int length = buffer.remaining();
        ensureCapacity(length);
        MemoryUtil.memCopy(MemoryUtil.memAddress(buffer), currentAddress, length);
        currentAddress += length;
        buffer.position(buffer.position() + length);
        return this;
    }

    public long getCurrentAddress() {
        return currentAddress;
    }

    public long getOffset() {
        return currentAddress - startAddress;
    }

    /**
     * Interface for handling buffer resizing events.
     */
    public interface ResizeCallback {
        /**
         * Called when the writer needs more space.
         * @param newCapacity The minimum new capacity required (in bytes).
         * @return The new memory address of the buffer start.
         */
        long resize(long newCapacity);
    }
}
