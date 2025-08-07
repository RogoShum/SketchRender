package rogo.sketchrender.vertexbuffer.component;

import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.vertexbuffer.BufferBuilder;
import rogo.sketchrender.vertexbuffer.attribute.Vertex;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class DynamicAttribute extends VertexAttribute {
    public DynamicAttribute(Vertex... vertices) {
        super(vertices);
    }

    @Override
    public void createBuffer() {
        this.buffer = MemoryUtil.memAlloc(16);
    }

    @Override
    public void init(Consumer<BufferBuilder<?>> bufferConsumer) {
    }

    @Override
    public void addAttrib(Consumer<ByteBuffer> bufferConsumer) {
        try {
            if (this.buffer.remaining() >= this.vertexSize) {
                bufferConsumer.accept(this.buffer);
            } else {
                ByteBuffer newBuffer = MemoryUtil.memAlloc(Math.max(16, this.buffer.capacity()) + (this.buffer.capacity() / 2));
                this.buffer.flip();
                newBuffer.put(this.buffer);
                MemoryUtil.memFree(this.buffer);
                this.buffer = newBuffer;
            }
        } catch (Exception e) {
            throw new RuntimeException("SketchRender Dynamic Attribute addAttrib error", e);
        }
    }

    @Override
    public boolean needUpdate() {
        return true;
    }
}