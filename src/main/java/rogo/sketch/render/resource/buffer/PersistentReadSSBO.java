package rogo.sketch.render.resource.buffer;

import io.netty.channel.unix.Buffer;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.api.BindingResource;
import rogo.sketch.api.DataResourceObject;
import rogo.sketch.util.KeyId;

import java.nio.ByteBuffer;

public class PersistentReadSSBO implements DataResourceObject, BindingResource {
    private int id;
    private boolean disposed = false;
    private long bufferPointer;
    private long capacity;
    private long dataCount;
    private final long stride;
    public int position;

    private ByteBuffer mappedBuffer;

    public PersistentReadSSBO(long dataCount, long stride) {
        this(dataCount, stride, MemoryUtil.nmemCalloc(dataCount, stride), GL15.glGenBuffers());
    }

    public PersistentReadSSBO(long dataCount, long stride, long memoryAddress, int handle) {
        long totalCapacity = dataCount * stride;
        if (totalCapacity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Buffer capacity too large");
        }
        this.capacity = totalCapacity;
        this.stride = stride;
        this.dataCount = dataCount;
        this.bufferPointer = memoryAddress;

        id = handle;
        if (id < 0) {
            throw new RuntimeException("Failed to create a new buffer");
        }

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL45.nglBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, capacity, bufferPointer,
                GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_READ_BIT);

        mappedBuffer = GL45.glMapBufferRange(
                GL43.GL_SHADER_STORAGE_BUFFER,
                0,
                capacity,
                GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_READ_BIT
        );

        MemoryUtil.nmemFree(bufferPointer);
        this.bufferPointer = Buffer.memoryAddress(mappedBuffer);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public int getHandle() {
        return id;
    }

    @Override
    public long getDataCount() {
        return dataCount;
    }

    @Override
    public long getCapacity() {
        return capacity;
    }

    @Override
    public long getMemoryAddress() {
        return bufferPointer;
    }

    public long getStride() {
        return stride;
    }

    public void ensureCapacity(int requiredCount, boolean force) {
        checkDisposed();

        if (requiredCount * stride <= capacity && !force) {
            return;
        }

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL45.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        GL15.glDeleteBuffers(id);

        long newCapacity = requiredCount * stride;
        long newBufferPointer = MemoryUtil.nmemCalloc(requiredCount, stride);
        this.dataCount = requiredCount;
        this.capacity = newCapacity;

        this.id = GL15.glGenBuffers();
        if (id < 0) {
            throw new RuntimeException("Failed to create a new buffer");
        }

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL45.nglBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, newCapacity, newBufferPointer,
                GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_READ_BIT);

        mappedBuffer = GL45.glMapBufferRange(
                GL43.GL_SHADER_STORAGE_BUFFER,
                0,
                newCapacity,
                GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_READ_BIT
        );

        MemoryUtil.nmemFree(newBufferPointer);
        this.bufferPointer = Buffer.memoryAddress(mappedBuffer);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void ensureCapacity(int requiredCount) {
        ensureCapacity(requiredCount, false);
    }

    public void dispose() {
        if (disposed) return;

        if (mappedBuffer != null) {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
            GL45.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
            mappedBuffer = null;
        }

        GL15.glDeleteBuffers(id);
        disposed = true;
    }

    private void checkDisposed() {
        if (disposed) {
            throw new IllegalStateException("Buffer has been disposed");
        }
    }

    public boolean isDisposed() {
        return disposed;
    }

    public void discardBufferId() {
        GL15.glDeleteBuffers(id);
    }

    public void discardMemory() {
        MemoryUtil.nmemFree(bufferPointer);
    }

    public ByteBuffer getMappedBuffer() {
        checkDisposed();
        return mappedBuffer;
    }

    public int getInt(long index) {
        checkDisposed();
        if (mappedBuffer == null) {
            throw new IllegalStateException("Buffer is not mapped");
        }
        return mappedBuffer.asIntBuffer().get((int) index);
    }

    public void sync() {
        checkDisposed();
        GL45.glMemoryBarrier(GL45.GL_SHADER_STORAGE_BARRIER_BIT);
    }

    @Override
    public void bind(KeyId resourceType, int binding) {
        checkDisposed();
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, id);
    }
}