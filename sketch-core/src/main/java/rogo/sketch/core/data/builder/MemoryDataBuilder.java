package rogo.sketch.core.data.builder;

import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.data.format.DataFormat;

import java.nio.ByteBuffer;

/**
 * DataBuilder implementation backed by a ByteBuffer (Direct or Heap).
 * Automatically expands the buffer if needed.
 */
public class MemoryDataBuilder implements DataBuilder {
    private final DataFormat format;
    private ByteBuffer buffer;
    private boolean autoGrow;
    private final boolean isDirect;

    public MemoryDataBuilder(DataFormat format, int initialCapacity) {
        this.format = format;
        this.buffer = MemoryUtil.memAlloc(initialCapacity);
        this.autoGrow = true;
        this.isDirect = true;
    }

    public MemoryDataBuilder(DataFormat format, ByteBuffer existingBuffer) {
        this.format = format;
        this.buffer = existingBuffer;
        this.autoGrow = false; // External buffers don't grow by default unless specified
        this.isDirect = existingBuffer.isDirect();
    }
    
    public void setAutoGrow(boolean autoGrow) {
        this.autoGrow = autoGrow;
    }

    @Override
    public DataFormat getFormat() {
        return format;
    }

    @Override
    public void ensureCapacity(int bytes) {
        if (buffer.remaining() < bytes) {
            if (!autoGrow) {
                throw new IndexOutOfBoundsException("Buffer capacity exceeded and auto-grow is disabled.");
            }
            expand(Math.max(buffer.capacity() * 2, buffer.capacity() + bytes));
        }
    }

    private void expand(int newCapacity) {
        if (!isDirect) {
            throw new UnsupportedOperationException("Cannot expand non-direct buffer automatically.");
        }
        ByteBuffer newBuffer = MemoryUtil.memAlloc(newCapacity);
        buffer.flip();
        newBuffer.put(buffer);
        MemoryUtil.memFree(buffer);
        buffer = newBuffer;
    }

    @Override
    public DataBuilder putByte(byte value) {
        ensureCapacity(1);
        buffer.put(value);
        return this;
    }

    @Override
    public DataBuilder putShort(short value) {
        ensureCapacity(2);
        buffer.putShort(value);
        return this;
    }

    @Override
    public DataBuilder putInt(int value) {
        ensureCapacity(4);
        buffer.putInt(value);
        return this;
    }

    @Override
    public DataBuilder putLong(long value) {
        ensureCapacity(8);
        buffer.putLong(value);
        return this;
    }

    @Override
    public DataBuilder putFloat(float value) {
        ensureCapacity(4);
        buffer.putFloat(value);
        return this;
    }

    @Override
    public DataBuilder putDouble(double value) {
        ensureCapacity(8);
        buffer.putDouble(value);
        return this;
    }

    @Override
    public DataBuilder putBoolean(boolean value) {
        return putByte((byte) (value ? 1 : 0));
    }

    @Override
    public DataBuilder putBytes(byte[] bytes) {
        ensureCapacity(bytes.length);
        buffer.put(bytes);
        return this;
    }

    @Override
    public DataBuilder putBytes(ByteBuffer src) {
        ensureCapacity(src.remaining());
        buffer.put(src);
        return this;
    }

    @Override
    public void skip(int bytes) {
        ensureCapacity(bytes);
        buffer.position(buffer.position() + bytes);
    }

    @Override
    public long offset() {
        return buffer.position();
    }

    @Override
    public void reset() {
        buffer.clear();
    }

    @Override
    public void finish() {
        // No-op for direct memory builder usually, 
        // but could be used to flip for reading if the API contract implies it.
        // Keeping it consistent with stream-like behavior.
    }

    /**
     * Get the underlying buffer. Note: Position is at the write cursor.
     */
    public ByteBuffer buffer() {
        return buffer;
    }
    
    /**
     * Get a readable slice of the data written so far.
     * Does not affect the main buffer's position.
     */
    public ByteBuffer getReadableBuffer() {
        ByteBuffer slice = buffer.duplicate(); // Share content, separate indices
        slice.flip();
        return slice;
    }

    public void free() {
        if (isDirect && autoGrow) { // Only free if we allocated it via memAlloc
            MemoryUtil.memFree(buffer);
        }
    }
}

