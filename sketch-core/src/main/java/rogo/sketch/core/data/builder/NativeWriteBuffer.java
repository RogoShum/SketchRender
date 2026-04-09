package rogo.sketch.core.data.builder;

import java.nio.ByteBuffer;

/**
 * Public native-memory writer surface used by transform uploads and other raw
 * buffer staging paths.
 */
public class NativeWriteBuffer extends NativeBufferWriterBase {
    public NativeWriteBuffer(ByteBuffer buffer) {
        super(buffer);
    }

    public NativeWriteBuffer(long address, long capacity, boolean externalMemory) {
        super(address, capacity, externalMemory);
    }

    public static NativeWriteBuffer createInternal(long capacity) {
        NativeBufferWriterBase builder = NativeBufferWriterBase.createInternal(capacity);
        return new NativeWriteBuffer(builder.getBaseAddress(), builder.getCapacity(), false);
    }

    public static NativeWriteBuffer getExternal(long address, long capacity) {
        return new NativeWriteBuffer(address, capacity, true);
    }

    public static NativeWriteBuffer getExternal(ByteBuffer buffer) {
        return new NativeWriteBuffer(buffer);
    }
}

