package rogo.sketch.core.resource.buffer;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL44;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.api.BufferResourceObject;
import rogo.sketch.core.data.Usage;
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
    private long mappedAddress = MemoryUtil.NULL;

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

    public void upload(long ptr, long dataSize, long maxSize) {
        if (GLFeatureChecker.supportsDSA()) {
            GL45.nglNamedBufferData(handle, dataSize, ptr, usage.getGLConstant());
        } else {
            bind();
            GL15.nglBufferData(GL15.GL_ARRAY_BUFFER, dataSize, ptr, usage.getGLConstant());
            unbind();
        }
        this.size = maxSize;
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

    public long mapPersistent(long capacity) {
        ensureCapacity(capacity);

        int access = GL44.GL_MAP_WRITE_BIT | GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT;

        if (GLFeatureChecker.supportsDSA()) {
            // DSA 重新分配存储时必须指定 flags，这里简化处理，假设 ensureCapacity 已经分配了 STORAGE_FLAGS
            // 注意：使用持久化映射通常需要 glBufferStorage 而不是 glBufferData
            // 这里仅做 map 演示
            mappedAddress = MemoryUtil.memAddress(GL45.glMapNamedBufferRange(handle, 0, capacity, access));
        } else {
            bind();
            mappedAddress = MemoryUtil.memAddress(GL45.glMapBufferRange(GL15.GL_ARRAY_BUFFER, 0, capacity, access));
            unbind();
        }
        return mappedAddress;
    }

    public void unmap() {
        if (mappedAddress != MemoryUtil.NULL) {
            if (GLFeatureChecker.supportsDSA()) {
                GL45.glUnmapNamedBuffer(handle);
            } else {
                bind();
                GL15.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);
                unbind();
            }
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
            GL15.glDeleteBuffers(handle);
            disposed = true;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}