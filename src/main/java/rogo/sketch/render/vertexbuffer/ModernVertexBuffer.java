package rogo.sketch.render.vertexbuffer;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import rogo.sketch.render.vertexbuffer.filler.ByteBufferFiller;
import rogo.sketch.render.vertexbuffer.filler.DataFiller;
import rogo.sketch.render.data.format.DataElement;
import rogo.sketch.render.data.format.DataFormat;

import java.nio.ByteBuffer;

/**
 * Modern vertex buffer implementation using the new format system
 */
public class ModernVertexBuffer implements AutoCloseable {
    private final DataFormat format;
    private final int vertexBufferID;
    private final int vertexArrayID;
    private final boolean isDynamic;
    private ByteBuffer buffer;
    private ByteBufferFiller filler;
    private int vertexCount;
    private int vertexCapacity;

    public ModernVertexBuffer(DataFormat format, int initialCapacity, boolean isDynamic) {
        this.format = format;
        this.isDynamic = isDynamic;
        this.vertexCapacity = initialCapacity;
        this.vertexCount = 0;

        // Create OpenGL objects
        this.vertexBufferID = GL15.glGenBuffers();
        this.vertexArrayID = GL30.glGenVertexArrays();

        // Allocate buffer
        allocateBuffer(initialCapacity);

        // Setup vertex array
        setupVertexArray();
    }

    private void allocateBuffer(int capacity) {
        if (buffer != null) {
            org.lwjgl.system.MemoryUtil.memFree(buffer);
        }

        int bufferSize = capacity * format.getStride();
        buffer = org.lwjgl.system.MemoryUtil.memAlloc(bufferSize);
        filler = ByteBufferFiller.wrap(format, buffer);
        this.vertexCapacity = capacity;

        // Upload to GPU
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBufferID);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer.capacity(), 
                         isDynamic ? GL15.GL_DYNAMIC_DRAW : GL15.GL_STATIC_DRAW);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private void setupVertexArray() {
        GL30.glBindVertexArray(vertexArrayID);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBufferID);

        for (DataElement element : format.getElements()) {
            GL20.glVertexAttribPointer(
                element.getIndex(),
                element.getComponentCount(),
                element.getGLType(),
                element.isNormalized(),
                format.getStride(),
                element.getOffset()
            );
            GL20.glEnableVertexAttribArray(element.getIndex());
        }

        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    /**
     * Begin filling vertex data
     */
    public DataFiller beginFill() {
        filler.reset();
        return filler;
    }

    /**
     * Finish filling and upload data to GPU
     */
    public void endFill() {
        endFill(vertexCount);
    }

    /**
     * Finish filling and upload data to GPU with specific vertex count
     */
    public void endFill(int vertexCount) {
        this.vertexCount = vertexCount;
        
        // Prepare buffer for uploading
        buffer.position(0);
        buffer.limit(vertexCount * format.getStride());

        // Upload to GPU
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBufferID);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, buffer);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        buffer.clear();
    }

    /**
     * Ensure the buffer has enough capacity for the specified number of vertices
     */
    public void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity > vertexCapacity) {
            int newCapacity = Math.max(requiredCapacity, vertexCapacity * 2);
            allocateBuffer(newCapacity);
            setupVertexArray();
        }
    }

    /**
     * Bind the vertex array for rendering
     */
    public void bind() {
        GL30.glBindVertexArray(vertexArrayID);
    }

    /**
     * Unbind the vertex array
     */
    public static void unbind() {
        GL30.glBindVertexArray(0);
    }

    /**
     * Draw the vertices
     */
    public void draw(int mode) {
        draw(mode, 0, vertexCount);
    }

    /**
     * Draw a subset of vertices
     */
    public void draw(int mode, int first, int count) {
        bind();
        GL15.glDrawArrays(mode, first, count);
        unbind();
    }

    /**
     * Draw with instancing
     */
    public void drawInstanced(int mode, int instanceCount) {
        drawInstanced(mode, 0, vertexCount, instanceCount);
    }

    /**
     * Draw a subset of vertices with instancing
     */
    public void drawInstanced(int mode, int first, int count, int instanceCount) {
        bind();
        GL31.glDrawArraysInstanced(mode, first, count, instanceCount);
        unbind();
    }

    /**
     * Check if this vertex buffer's format is compatible with another format
     */
    public boolean isCompatibleWith(DataFormat otherFormat) {
        return format.isCompatibleWith(otherFormat);
    }

    /**
     * Check if this vertex buffer's format exactly matches another format
     */
    public boolean formatMatches(DataFormat otherFormat) {
        return format.matches(otherFormat);
    }

    // Getters
    public DataFormat getFormat() {
        return format;
    }

    public int getVertexCount() {
        return vertexCount;
    }

    public int getVertexCapacity() {
        return vertexCapacity;
    }

    public int getVertexBufferID() {
        return vertexBufferID;
    }

    public int getVertexArrayID() {
        return vertexArrayID;
    }

    public boolean isDynamic() {
        return isDynamic;
    }

    @Override
    public void close() {
        if (buffer != null) {
            org.lwjgl.system.MemoryUtil.memFree(buffer);
            buffer = null;
        }

        if (vertexBufferID > 0) {
            GL15.glDeleteBuffers(vertexBufferID);
        }

        if (vertexArrayID > 0) {
            GL30.glDeleteVertexArrays(vertexArrayID);
        }
    }

    /**
     * Create a static vertex buffer with the specified format and capacity
     */
    public static ModernVertexBuffer createStatic(DataFormat format, int capacity) {
        return new ModernVertexBuffer(format, capacity, false);
    }

    /**
     * Create a dynamic vertex buffer with the specified format and capacity
     */
    public static ModernVertexBuffer createDynamic(DataFormat format, int capacity) {
        return new ModernVertexBuffer(format, capacity, true);
    }
}