package rogo.sketch.core.resource.buffer;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL44;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.api.BufferResourceObject;
import rogo.sketch.core.data.Usage;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.driver.internal.IGLBufferStrategy;

import java.nio.ByteBuffer;

/**
 * Represents a single OpenGL Vertex Buffer Object (VBO).
 * Managed as a component of a VertexResource (VAO).
 * Uses GraphicsAPI buffer strategy for DSA/Legacy abstraction.
 */
public class VertexBufferObject implements BufferResourceObject {
    private final int handle;
    private final Usage usage;
    private long size;
    private boolean disposed = false;
    private long mappedAddress = MemoryUtil.NULL;

    /**
     * Get the buffer strategy from the current graphics API
     */
    private static IGLBufferStrategy getBufferStrategy() {
        return GraphicsDriver.getCurrentAPI().getBufferStrategy();
    }

    public VertexBufferObject(Usage usage) {
        this.usage = usage;
        this.handle = getBufferStrategy().createBuffer();
        this.size = 0;
    }

    public VertexBufferObject(int size, Usage usage) {
        this(usage);
        ensureCapacity(size);
    }

    public void bind() {
        getBufferStrategy().bindBuffer(GL15.GL_ARRAY_BUFFER, handle);
    }

    public void unbind() {
        getBufferStrategy().bindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public void upload(ByteBuffer data) {
        getBufferStrategy().bufferData(handle, data, usage.getGLConstant());
        this.size = data.limit();
    }

    public void upload(long ptr, long dataSize, long maxSize) {
        getBufferStrategy().bufferData(handle, dataSize, ptr, usage.getGLConstant());
        this.size = maxSize;
    }

    public void uploadSubData(long offset, ByteBuffer data) {
        getBufferStrategy().bufferSubData(handle, offset, data);
    }

    public void ensureCapacity(long requiredSize) {
        if (this.size < requiredSize) {
            // Allocate new storage with null data pointer
            getBufferStrategy().bufferData(handle, requiredSize, MemoryUtil.NULL, usage.getGLConstant());
            this.size = requiredSize;
        }
    }

    public long mapPersistent(long capacity) {
        ensureCapacity(capacity);

        int access = GL44.GL_MAP_WRITE_BIT | GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT;

        ByteBuffer mapped = getBufferStrategy().mapBufferRange(handle, GL15.GL_ARRAY_BUFFER, 0, capacity, access);
        if (mapped != null) {
            mappedAddress = MemoryUtil.memAddress(mapped);
        } else {
            mappedAddress = MemoryUtil.NULL;
        }
        return mappedAddress;
    }

    public void unmap() {
        if (mappedAddress != MemoryUtil.NULL) {
            getBufferStrategy().unmapBuffer(handle, GL15.GL_ARRAY_BUFFER);
            mappedAddress = MemoryUtil.NULL;
        }
    }

    public long getMappedAddress() {
        return mappedAddress;
    }

    @Override
    public int getHandle() {
        return handle;
    }

    @Override
    public void dispose() {
        if (!disposed) {
            getBufferStrategy().deleteBuffer(handle);
            disposed = true;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
