package rogo.sketch.render.vertexbuffer;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class ByteBufferBuilder extends BufferBuilder<ByteBuffer> {

    public static ByteBufferBuilder builder() {
        return new ByteBufferBuilder();
    }

    @Override
    protected ByteBuffer _buildBuffer() {
        ByteBuffer buffer = MemoryUtil.memAlloc(size);
        for (Number number : list) {
            if (number instanceof Byte b) {
                buffer.put(b);
            } else if (number instanceof Short s) {
                buffer.putShort(s);
            } else if (number instanceof Integer i) {
                buffer.putInt(i);
            } else if (number instanceof Long l) {
                buffer.putLong(l);
            } else if (number instanceof Float f) {
                buffer.putFloat(f);
            } else if (number instanceof Double d) {
                buffer.putDouble(d);
            }
        }
        return buffer;
    }
}