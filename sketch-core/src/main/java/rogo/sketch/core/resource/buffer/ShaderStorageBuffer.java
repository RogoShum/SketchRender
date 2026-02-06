package rogo.sketch.core.resource.buffer;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.api.BindingResource;
import rogo.sketch.core.api.DataResourceObject;
import rogo.sketch.core.api.Resizeable;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.driver.internal.IGLBufferStrategy;
import rogo.sketch.core.util.KeyId;

public class ShaderStorageBuffer implements DataResourceObject, BindingResource, Resizeable {
    private final int id;
    private boolean disposed = false;
    private long bufferPointer;
    private long capacity;
    private long dataCount;
    private final long stride;
    public long position;

    /**
     * Get the buffer strategy from the current graphics API
     */
    private static IGLBufferStrategy getBufferStrategy() {
        return GraphicsDriver.getCurrentAPI().getBufferStrategy();
    }

    public ShaderStorageBuffer(long dataCount, long stride, int usage) {
        this(dataCount, stride, MemoryUtil.nmemCalloc(dataCount, stride), usage);
    }

    public ShaderStorageBuffer(long dataCount, long stride, long memoryAddress, int usage) {
        long totalCapacity = dataCount * stride;
        if (totalCapacity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Buffer capacity too large");
        }
        this.capacity = totalCapacity;
        this.stride = stride;
        this.dataCount = dataCount;
        bufferPointer = memoryAddress;

        IGLBufferStrategy strategy = getBufferStrategy();
        id = strategy.createBuffer();
        if (id < 0) {
            throw new RuntimeException("Failed to create a new buffer");
        }
        strategy.bufferData(GL43.GL_SHADER_STORAGE_BUFFER,id, this.capacity, bufferPointer, usage);
    }

    public ShaderStorageBuffer(DataResourceObject buffer) {
        this.capacity = buffer.getCapacity();
        this.bufferPointer = buffer.getMemoryAddress();
        this.id = buffer.getHandle();
        this.stride = buffer.getStride();

        IGLBufferStrategy strategy = getBufferStrategy();
        strategy.bufferData(GL43.GL_SHADER_STORAGE_BUFFER, id, capacity, bufferPointer, GL15.GL_DYNAMIC_DRAW);
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

    public void upload() {
        checkDisposed();
        getBufferStrategy().bufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, id, 0, position, bufferPointer);
    }

    public void upload(long index) {
        checkDisposed();
        long indexOffset = index * getStride();
        getBufferStrategy().bufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, id, indexOffset, getStride(), bufferPointer + indexOffset);
    }

    public void upload(long index, int stride) {
        checkDisposed();
        long indexOffset = index * stride;
        getBufferStrategy().bufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, id, indexOffset, stride, bufferPointer + indexOffset);
    }

    public void resetUpload(int usage) {
        checkDisposed();
        getBufferStrategy().bufferData(GL43.GL_SHADER_STORAGE_BUFFER, id, capacity, bufferPointer, usage);
    }

    public void ensureCapacity(int requiredCount, boolean copy, boolean force) {
        checkDisposed();

        if (requiredCount * stride <= capacity && !force) {
            return;
        }

        long newBufferPointer = MemoryUtil.nmemCalloc(requiredCount, stride);
        long prevCapacity = capacity;
        this.capacity = requiredCount * stride;

        if (copy) {
            MemoryUtil.memCopy(bufferPointer, newBufferPointer, Math.min(capacity, prevCapacity));
        }

        MemoryUtil.nmemFree(bufferPointer);
        this.bufferPointer = newBufferPointer;
        this.dataCount = requiredCount;

        resetUpload(GL15.GL_DYNAMIC_DRAW);
    }

    public void ensureCapacity(int requiredCount, boolean copy) {
        ensureCapacity(requiredCount, copy, false);
    }

    public void setBufferPointer(long bufferPointer) {
        checkDisposed();
        this.bufferPointer = bufferPointer;
    }

    public void setCapacity(long capacity) {
        checkDisposed();
        this.capacity = capacity;
    }

    public void dispose() {
        if (disposed) return;
        MemoryUtil.nmemFree(bufferPointer);
        getBufferStrategy().deleteBuffer(id);
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private void checkDisposed() {
        if (disposed) {
            throw new IllegalStateException("Buffer has been disposed");
        }
    }

    public void discardBufferId() {
        getBufferStrategy().deleteBuffer(id);
    }

    public void discardMemory() {
        MemoryUtil.nmemFree(bufferPointer);
    }

    @Override
    public long resize(long newCapacity) {
        checkDisposed();

        // Calculate new aligned capacity (optional, stride alignment)
        if (newCapacity <= capacity) return bufferPointer;

        long newPointer = MemoryUtil.nmemCalloc(newCapacity, 1);

        // Copy old data
        if (bufferPointer != 0) {
            MemoryUtil.memCopy(bufferPointer, newPointer, Math.min(capacity, newCapacity));
            MemoryUtil.nmemFree(bufferPointer);
        }

        this.bufferPointer = newPointer;
        this.capacity = newCapacity;

        // Re-upload/Reset buffer storage
        // Note: For SSBO, usually we expand then upload.
        // But if this is called during mapping/writing, we just update the pointer.
        // The actual GPU upload happens later via upload().
        // However, we must ensure the GL buffer is resized eventually.
        // Here we just resize the client-side shadow copy.
        // We should trigger GL resize now or mark dirty?
        // Current design: ensureCapacity() calls resetUpload().

        resetUpload(GL15.GL_DYNAMIC_DRAW);

        return bufferPointer;
    }

    @Override
    public void bind(KeyId resourceType, int binding) {
        checkDisposed();
        getBufferStrategy().bindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, id);
    }
}