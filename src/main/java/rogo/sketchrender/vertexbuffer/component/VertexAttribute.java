package rogo.sketchrender.vertexbuffer.component;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.vertexbuffer.BufferBuilder;
import rogo.sketchrender.vertexbuffer.attribute.GLVertex;

import javax.annotation.Nullable;
import java.nio.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public abstract class VertexAttribute implements AutoCloseable {
    protected final List<GLVertex> vertices = new ArrayList<>();
    protected final int vertexID;
    protected final int vertexSize;
    private int vertexCount;
    @Nullable
    protected ByteBuffer buffer;

    public VertexAttribute(GLVertex... vertices) {
        this.vertices.addAll(Arrays.stream(vertices).toList());
        vertexID = GlStateManager._glGenBuffers();
        int vertexSize = 0;
        if (this.vertices.size() > 1) {
            for (GLVertex vertex : vertices) {
                vertexSize += vertex.size() * vertex.elementType().getSize();
            }
        }
        this.vertexSize = vertexSize;
        createBuffer();
    }

    public abstract void createBuffer();

    public int vertexCount() {
        return vertexCount;
    }

    public void setVertexCount(int count) {
        this.vertexCount = count;
    }

    public abstract void init(Consumer<BufferBuilder<?>> bufferConsumer);

    public abstract void addAttrib(Consumer<ByteBuffer> bufferConsumer);

    public void bind() {
        GL15.glBindBuffer(34962, vertexID);
    }

    public void updateVertexAttrib() {
        if (needUpdate()) {
            bind();
            buffer.flip();
            GL15.glBufferData(34962, buffer, 35048);
            buffer.limit(buffer.capacity());
        }
    }

    public void bindVertexAttribArray() {
        bind();
        int offset = 0;
        for (GLVertex vertex : vertices) {
            GL20.glVertexAttribPointer(vertex.index(), vertex.size(), vertex.elementType().getGlType(), false, vertexSize, offset);
            GL20.glEnableVertexAttribArray(vertex.index());
            if (needUpdate())
                GL33.glVertexAttribDivisor(vertex.index(), 1);
            offset += vertex.size() * vertex.elementType().getSize();
        }
    }

    public abstract boolean needUpdate();

    @Override
    public void close() {
        RenderSystem.glDeleteBuffers(this.vertexID);
        if (this.buffer != null) {
            MemoryUtil.memFree(this.buffer);
        }
    }
}
