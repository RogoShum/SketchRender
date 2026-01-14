package rogo.sketch.render.data.builder;

import org.lwjgl.system.MemoryUtil;
import rogo.sketch.render.data.format.DataFormat;

import java.nio.ByteBuffer;

/**
 * DataBuilder implementation that writes directly to a memory address (Unsafe).
 * Useful for SSBOs, mapped buffers, or off-heap memory management.
 * 
 * WARNING: Does NOT perform bounds checking by default for performance.
 * Use with caution.
 */
public class AddressDataBuilder implements DataBuilder {
    private final DataFormat format;
    private long address;
    private long capacity;
    private long offset;

    public AddressDataBuilder(DataFormat format, long address, long capacity) {
        this.format = format;
        this.address = address;
        this.capacity = capacity;
        this.offset = 0;
    }

    @Override
    public DataFormat getFormat() {
        return format;
    }

    @Override
    public void ensureCapacity(int bytes) {
        if (offset + bytes > capacity) {
            throw new IndexOutOfBoundsException("AddressDataBuilder overflow: " + (offset + bytes) + " > " + capacity);
        }
    }

    @Override
    public DataBuilder putByte(byte value) {
        MemoryUtil.memPutByte(address + offset, value);
        offset += 1;
        return this;
    }

    @Override
    public DataBuilder putShort(short value) {
        MemoryUtil.memPutShort(address + offset, value);
        offset += 2;
        return this;
    }

    @Override
    public DataBuilder putInt(int value) {
        MemoryUtil.memPutInt(address + offset, value);
        offset += 4;
        return this;
    }

    @Override
    public DataBuilder putLong(long value) {
        MemoryUtil.memPutLong(address + offset, value);
        offset += 8;
        return this;
    }

    @Override
    public DataBuilder putFloat(float value) {
        MemoryUtil.memPutFloat(address + offset, value);
        offset += 4;
        return this;
    }

    @Override
    public DataBuilder putDouble(double value) {
        MemoryUtil.memPutDouble(address + offset, value);
        offset += 8;
        return this;
    }

    @Override
    public DataBuilder putBoolean(boolean value) {
        return putByte((byte) (value ? 1 : 0));
    }

    @Override
    public DataBuilder putBytes(byte[] bytes) {
        for (byte b : bytes) {
            putByte(b);
        }
        return this;
    }

    @Override
    public DataBuilder putBytes(ByteBuffer buffer) {
        int length = buffer.remaining();
        long srcAddress = MemoryUtil.memAddress(buffer);
        MemoryUtil.memCopy(srcAddress + buffer.position(), address + offset, length);
        offset += length;
        buffer.position(buffer.position() + length);
        return this;
    }

    @Override
    public void skip(int bytes) {
        offset += bytes;
    }

    @Override
    public long offset() {
        return offset;
    }
    
    public void setOffset(long offset) {
        this.offset = offset;
    }
    
    public long getBaseAddress() {
        return address;
    }

    @Override
    public void reset() {
        offset = 0;
    }

    @Override
    public void finish() {
        // No-op
    }
}

