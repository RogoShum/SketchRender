package rogo.sketchrender.vertexbuffer.component;

import rogo.sketchrender.vertexbuffer.attribute.GLVertex;

import java.nio.FloatBuffer;
import java.util.function.Consumer;

public class MainAttribute extends VertexAttribute {
    public MainAttribute(GLVertex... vertices) {
        super(vertices);
    }

    @Override
    public void addAttrib(Consumer<FloatBuffer> bufferConsumer) {}

    @Override
    public void init(Consumer<FloatBuffer> bufferConsumer) {
        bufferConsumer.accept(this.buffer);
        int count = 0;
        for (GLVertex vertex : vertices) {
            count += vertex.size();
        }
        setVertexCount(this.buffer.limit() / count);
    }

    @Override
    public boolean needUpdate() {
        return false;
    }
}
