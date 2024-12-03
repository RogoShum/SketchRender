package rogo.sketchrender.vertexbuffer.component;

import org.lwjgl.opengl.GL15;
import rogo.sketchrender.vertexbuffer.BufferBuilder;
import rogo.sketchrender.vertexbuffer.ByteBufferBuilder;
import rogo.sketchrender.vertexbuffer.attribute.GLVertex;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class StaticAttribute extends VertexAttribute {
    public StaticAttribute(GLVertex... vertices) {
        super(vertices);
    }

    @Override
    public void createBuffer() {}

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
        for (GLVertex vertex : vertices) {
            count += vertex.size() * vertex.elementType().getSize();
        }
        setVertexCount(size / count);
    }

    @Override
    public boolean needUpdate() {
        return false;
    }
}
