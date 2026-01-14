package rogo.sketch.render.data.builder;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Implementation of DataBufferWriter using a ByteBuffer (Heap or Direct).
 * Automatically grows the buffer if needed (unless wrapped).
 */
public class MemoryBufferWriter implements DataBufferWriter {
    private ByteBuffer buffer;
    private final boolean autoGrow;
    private final int growthSize;

    public MemoryBufferWriter(ByteBuffer buffer) {
        this(buffer, false, 0);
    }

    public MemoryBufferWriter(int initialCapacity) {
        this(MemoryUtil.memAlloc(initialCapacity), true, 2097152); // 2MB default growth
    }

    private MemoryBufferWriter(ByteBuffer buffer, boolean autoGrow, int growthSize) {
        this.buffer = buffer;
        this.autoGrow = autoGrow;
        this.growthSize = growthSize;
        
        // Ensure native order for performance
        if (buffer.order() != ByteOrder.nativeOrder()) {
            buffer.order(ByteOrder.nativeOrder());
        }
    }
    
    public ByteBuffer getBuffer() {
        return buffer;
    }

    @Override
    public DataBufferWriter putFloat(float value) {
        ensureCapacity(Float.BYTES);
        buffer.putFloat(value);
        return this;
    }

    @Override
    public DataBufferWriter putInt(int value) {
        ensureCapacity(Integer.BYTES);
        buffer.putInt(value);
        return this;
    }

    @Override
    public DataBufferWriter putUInt(int value) {
        ensureCapacity(Integer.BYTES);
        buffer.putInt(value);
        return this;
    }

    @Override
    public DataBufferWriter putByte(byte value) {
        ensureCapacity(Byte.BYTES);
        buffer.put(value);
        return this;
    }

    @Override
    public DataBufferWriter putUByte(byte value) {
        ensureCapacity(Byte.BYTES);
        buffer.put(value);
        return this;
    }

    @Override
    public DataBufferWriter putShort(short value) {
        ensureCapacity(Short.BYTES);
        buffer.putShort(value);
        return this;
    }

    @Override
    public DataBufferWriter putUShort(short value) {
        ensureCapacity(Short.BYTES);
        buffer.putShort(value);
        return this;
    }

    @Override
    public DataBufferWriter putDouble(double value) {
        ensureCapacity(Double.BYTES);
        buffer.putDouble(value);
        return this;
    }
    
    @Override
    public DataBufferWriter putLong(long value) {
        ensureCapacity(Long.BYTES);
        buffer.putLong(value);
        return this;
    }

    @Override
    public void advance(int bytes) {
        ensureCapacity(bytes);
        buffer.position(buffer.position() + bytes);
    }

    @Override
    public long getWriteOffset() {
        return buffer.position();
    }

    @Override
    public void setWriteOffset(long offset) {
        if (offset > buffer.limit()) {
             ensureCapacity((int)(offset - buffer.position()));
        }
        buffer.position((int) offset);
    }

    @Override
    public void ensureCapacity(int additionalBytes) {
        if (buffer.remaining() < additionalBytes) {
            if (!autoGrow) {
                throw new IndexOutOfBoundsException("Buffer capacity exceeded");
            }
            
            int newCapacity = Math.max(
                    buffer.capacity() * 2,
                    buffer.capacity() + additionalBytes + growthSize
            );
            
            ByteBuffer newBuffer = MemoryUtil.memAlloc(newCapacity);
            buffer.flip();
            newBuffer.put(buffer);
            
            // If the old buffer was allocated by us (implied by autoGrow for this class usage usually),
            // we should free it if it's direct. 
            // NOTE: If wrapped buffer was passed and autoGrow=true, we assume ownership after resize.
            if (buffer.isDirect()) {
                MemoryUtil.memFree(buffer);
            }
            
            buffer = newBuffer;
        }
    }

    @Override
    public boolean supportsRandomAccess() {
        return true;
    }

    @Override
    public void putFloatAt(long byteOffset, float value) {
        buffer.putFloat((int) byteOffset, value);
    }

    @Override
    public void putIntAt(long byteOffset, int value) {
        buffer.putInt((int) byteOffset, value);
    }

    @Override
    public void putByteAt(long byteOffset, byte value) {
        buffer.put((int) byteOffset, value);
    }

    @Override
    public void putShortAt(long byteOffset, short value) {
        buffer.putShort((int) byteOffset, value);
    }

    @Override
    public void putDoubleAt(long byteOffset, double value) {
        buffer.putDouble((int) byteOffset, value);
    }
    
    @Override
    public DataBufferWriter put(ByteBuffer src) {
        ensureCapacity(src.remaining());
        buffer.put(src);
        return this;
    }
    
    public void dispose() {
        if (autoGrow && buffer != null && buffer.isDirect()) {
            MemoryUtil.memFree(buffer);
            buffer = null;
        }
    }
}

