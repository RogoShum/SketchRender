package rogo.sketch.render.data.filler;

import java.nio.ByteBuffer;

/**
 * WriteStrategy implementation for ByteBuffer backend
 */
public class ByteBufferWriteStrategy implements WriteStrategy {
    private final ByteBuffer buffer;

    public ByteBufferWriteStrategy(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public void writeFloat(float value) {
        buffer.putFloat(value);
    }

    @Override
    public void writeInt(int value) {
        buffer.putInt(value);
    }

    @Override
    public void writeByte(byte value) {
        buffer.put(value);
    }

    @Override
    public void writeShort(short value) {
        buffer.putShort(value);
    }

    @Override
    public void writeDouble(double value) {
        buffer.putDouble(value);
    }

    @Override
    public void writeFloatAt(long byteOffset, float value) {
        buffer.putFloat((int) byteOffset, value);
    }

    @Override
    public void writeIntAt(long byteOffset, int value) {
        buffer.putInt((int) byteOffset, value);
    }

    @Override
    public void writeByteAt(long byteOffset, byte value) {
        buffer.put((int) byteOffset, value);
    }

    @Override
    public void writeShortAt(long byteOffset, short value) {
        buffer.putShort((int) byteOffset, value);
    }

    @Override
    public void writeDoubleAt(long byteOffset, double value) {
        buffer.putDouble((int) byteOffset, value);
    }

    @Override
    public long getCurrentPosition() {
        return buffer.position();
    }

    @Override
    public void setPosition(long position) {
        buffer.position((int) position);
    }

    @Override
    public void advance(int bytes) {
        buffer.position(buffer.position() + bytes);
    }

    @Override
    public void finish() {
        buffer.flip();
    }

    @Override
    public void reset() {
        buffer.clear();
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
        return buffer.capacity();
    }

    /**
     * Get the underlying ByteBuffer
     */
    public ByteBuffer getBuffer() {
        return buffer;
    }
}
