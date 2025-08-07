package rogo.sketchrender.vertexbuffer.component;

import com.mojang.blaze3d.vertex.VertexFormat;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import rogo.sketchrender.vertexbuffer.BufferBuilder;
import rogo.sketchrender.vertexbuffer.DrawMode;
import rogo.sketchrender.vertexbuffer.attribute.Vertex;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class VertexBufferResource implements AutoCloseable {
    protected final VertexAttribute staticBuffer;
    protected final VertexAttribute dynamicBuffer;
    private int arrayObjectId;
    protected int indexCount;
    protected int instanceCount;
    protected final VertexFormat.Mode mode;
    protected final DrawMode drawMode;

    public VertexBufferResource(VertexFormat.Mode mode, DrawMode drawMode, Vertex[] mainVertices, Consumer<BufferBuilder<?>> bufferConsumer, Vertex[] mutableVertices) {
        this.staticBuffer = staticAttributeSuppler().apply(mainVertices);
        this.dynamicBuffer = mutableAttributeSuppler().apply(mutableVertices);
        this.arrayObjectId = GL30.glGenVertexArrays();
        this.mode = mode;
        this.drawMode = drawMode;
        init(bufferConsumer);
    }

    protected Function<Vertex[], StaticAttribute> staticAttributeSuppler() {
        return StaticAttribute::new;
    }

    protected Function<Vertex[], DynamicAttribute> mutableAttributeSuppler() {
        return DynamicAttribute::new;
    }

    public void init(Consumer<BufferBuilder<?>> bufferConsumer) {
        bindVertexArray();
        staticBuffer.bindVertexAttribArray();
        staticBuffer.init(bufferConsumer);
        dynamicBuffer.bindVertexAttribArray();
        unbindVertexArray();
        unbindVBO();
        this.indexCount = mode.indexCount(staticBuffer.vertexCount());
    }

    public void addInstanceAttrib(Consumer<ByteBuffer> consumer) {
        dynamicBuffer.addAttrib(consumer);
        instanceCount++;
    }

    public void unbindVBO() {
        GL15.glBindBuffer(34962, 0);
    }

    private void bindVertexArray() {
        GL30.glBindVertexArray(this.arrayObjectId);
    }

    public static void unbindVertexArray() {
        GL30.glBindVertexArray(0);
    }

    public void draw() {
//        if (this.instanceCount > 0 || drawMode == DrawMode.NORMAL) {
//            RenderSystem.assertOnRenderThread();
//
//            dynamicBuffer.updateVertexAttrib();
//            unbindVBO();
//            bindVertexArray();
//            shader.apply();
//
//            if (drawMode == DrawMode.INSTANCED) {
//                GL31.glDrawArraysInstanced(this.mode.asGLMode, 0, this.indexCount, this.instanceCount);
//            } else {
//                GL31.glDrawArrays(this.mode.asGLMode, 0, this.indexCount);
//            }
//
//            shader.clear();
//            unbindVertexArray();
//            this.instanceCount = 0;
//        }
    }

    public void close() {
        staticBuffer.close();
        dynamicBuffer.close();

        if (this.arrayObjectId > 0) {
            GL30.glDeleteVertexArrays(this.arrayObjectId);
            this.arrayObjectId = 0;
        }
    }
}