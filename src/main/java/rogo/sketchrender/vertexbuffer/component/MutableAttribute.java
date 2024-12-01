package rogo.sketchrender.vertexbuffer.component;

import rogo.sketchrender.vertexbuffer.attribute.GLVertex;

import java.nio.FloatBuffer;
import java.util.function.Consumer;

public class MutableAttribute extends VertexAttribute {
    public MutableAttribute(GLVertex... vertices) {
        super(vertices);
    }

    @Override
    public void init(Consumer<FloatBuffer> bufferConsumer) {}

    @Override
    public void addAttrib(Consumer<FloatBuffer> bufferConsumer) {
        try {
            bufferConsumer.accept(this.buffer);
        } catch (Exception e) {
            this.buffer.position(0);
        }
    }

    @Override
    public boolean needUpdate() {
        return true;
    }
}
