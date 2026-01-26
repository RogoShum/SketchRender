package rogo.sketch.core.resource.buffer;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.api.BufferResourceObject;
import rogo.sketch.core.data.Usage;
import rogo.sketch.core.data.builder.DataBufferWriter;
import rogo.sketch.core.data.builder.MemoryBufferWriter;
import rogo.sketch.core.util.GLFeatureChecker;

import java.nio.ByteBuffer;

/**
 * Represents a single OpenGL Vertex Buffer Object (VBO).
 * Managed as a component of a VertexResource (VAO).
 */
public class VertexBufferObject implements BufferResourceObject {
    private final int handle;
    private final Usage usage;
    private long size;
    private boolean disposed = false;

    public VertexBufferObject(Usage usage) {
        this.usage = usage;
        this.handle = GLFeatureChecker.supportsDSA() ? GL45.glCreateBuffers() : GL15.glGenBuffers();
        this.size = 0;
    }
    
    public VertexBufferObject(int size, Usage usage) {
        this(usage);
        ensureCapacity(size);
    }

    public void bind() {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, handle);
    }

    public void unbind() {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public void upload(ByteBuffer data) {
        if (GLFeatureChecker.supportsDSA()) {
            GL45.glNamedBufferData(handle, data, usage.getGLConstant());
        } else {
            bind();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, usage.getGLConstant());
            unbind();
        }
        this.size = data.limit();
    }
    
    public void uploadSubData(long offset, ByteBuffer data) {
        if (GLFeatureChecker.supportsDSA()) {
            GL45.glNamedBufferSubData(handle, offset, data);
        } else {
            bind();
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, offset, data);
            unbind();
        }
    }

    public void ensureCapacity(long requiredSize) {
        if (this.size < requiredSize) {
            // Allocate new storage
            if (GLFeatureChecker.supportsDSA()) {
                GL45.glNamedBufferData(handle, requiredSize, usage.getGLConstant());
            } else {
                bind();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, requiredSize, usage.getGLConstant());
                unbind();
            }
            this.size = requiredSize;
        }
    }
    
    /**
     * Upload data from a DataBufferWriter.
     * If the writer is a MemoryBufferWriter, we can directly access the buffer.
     * Otherwise, we might need a copy or mapping (not implemented for address writers yet).
     */
    public void upload(DataBufferWriter writer) {
        if (writer instanceof MemoryBufferWriter memoryWriter) {
            ByteBuffer buffer = memoryWriter.getBuffer();
            buffer.flip();
            upload(buffer);
        } else {
            throw new UnsupportedOperationException("Direct upload from non-memory writer not supported yet");
        }
    }

    @Override
    public int getHandle() {
        return handle;
    }

    @Override
    public void dispose() {
        if (!disposed) {
            GL15.glDeleteBuffers(handle);
            disposed = true;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}