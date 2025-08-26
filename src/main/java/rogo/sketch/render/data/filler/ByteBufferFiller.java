package rogo.sketch.render.data.filler;

import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.data.format.DataElement;

import java.nio.ByteBuffer;

/**
 * Data filler implementation that writes to a ByteBuffer
 */
public class ByteBufferFiller extends DataFiller {
    private final ByteBuffer buffer;

    public ByteBufferFiller(DataFormat format, ByteBuffer buffer) {
        super(format);
        this.buffer = buffer;
    }

    @Override
    public DataFiller vertex(long index) {
        super.vertex(index);
        // Set buffer position to the start of the vertex
        buffer.position((int) (index * format.getStride()));
        return this;
    }

    @Override
    public DataFiller element(int elementIndex) {
        super.element(elementIndex);
        // Set buffer position to the element within the current vertex
        DataElement element = format.getElement(elementIndex);
        long vertexStart = currentVertex * format.getStride();
        buffer.position((int) (vertexStart + element.getOffset()));
        return this;
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
    public void writeUInt(int value) {
        buffer.putInt(value);
    }

    @Override
    public void writeByte(byte value) {
        buffer.put(value);
    }

    @Override
    public void writeUByte(byte value) {
        buffer.put(value);
    }

    @Override
    public void writeShort(short value) {
        buffer.putShort(value);
    }

    @Override
    public void writeUShort(short value) {
        buffer.putShort(value);
    }

    @Override
    public void writeDouble(double value) {
        buffer.putDouble(value);
    }

    /**
     * Get the underlying ByteBuffer
     */
    public ByteBuffer getBuffer() {
        return buffer;
    }

    /**
     * Prepare the buffer for reading (flip it)
     */
    public ByteBuffer prepareForReading() {
        buffer.flip();
        return buffer;
    }

    /**
     * Reset the buffer for writing
     */
    public ByteBufferFiller reset() {
        buffer.clear();
        currentVertex = 0;
        currentElementIndex = 0;
        return this;
    }

    /**
     * Create a new ByteBufferFiller with the specified capacity
     */
    public static ByteBufferFiller create(DataFormat format, int vertexCount) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(format.getStride() * vertexCount);
        return new ByteBufferFiller(format, buffer);
    }

    /**
     * Create a new ByteBufferFiller wrapping an existing buffer
     */
    public static ByteBufferFiller wrap(DataFormat format, ByteBuffer buffer) {
        return new ByteBufferFiller(format, buffer);
    }
    
    @Override
    public void end() {
        // For ByteBufferFiller, just prepare the buffer for reading
        buffer.flip();
    }
}