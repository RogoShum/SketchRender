package rogo.sketch.render.vertexbuffer.component;

import org.lwjgl.opengl.GL15;
import rogo.sketch.render.vertexbuffer.BufferBuilder;
import rogo.sketch.render.vertexbuffer.ByteBufferBuilder;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class StaticAttribute extends VertexAttribute {
    public StaticAttribute(Vertex... vertices) {
        super(vertices);
    }

    @Override
    public void createBuffer() {
    }

    @Override
    public void addAttrib(Consumer<ByteBuffer> bufferConsumer) {
    }

    @Override
    public void init(Consumer<BufferBuilder<?>> bufferConsumer) {
        ByteBufferBuilder builder = ByteBufferBuilder.builder();
        bufferConsumer.accept(builder);
        int size = builder.size();
        GL15.glBufferData(34962, builder.buildFlipBuffer(), 35044);
        int count = 0;
        for (Vertex vertex : vertices) {
            count += vertex.count() * vertex.size();
        }
        setVertexCount(size / count);
    }

    @Override
    public boolean needUpdate() {
        return false;
    }
}