package rogo.sketch.render.data.filler;

import rogo.sketch.render.data.format.DataFormat;
import java.nio.ByteBuffer;

/**
 * Data filler implementation that writes to a ByteBuffer
 * Uses ByteBufferWriteStrategy for implementation
 */
public class ByteBufferFiller extends DirectDataFiller {
    
    public ByteBufferFiller(DataFormat format, ByteBuffer buffer) {
        super(format, new ByteBufferWriteStrategy(buffer));
    }

    /**
     * Get the underlying ByteBuffer
     */
    public ByteBuffer getBuffer() {
        return ((ByteBufferWriteStrategy) writeStrategy).getBuffer();
    }

    /**
     * Prepare the buffer for reading (flip it)
     */
    public ByteBuffer prepareForReading() {
        ByteBuffer buffer = getBuffer();
        buffer.flip();
        return buffer;
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
}