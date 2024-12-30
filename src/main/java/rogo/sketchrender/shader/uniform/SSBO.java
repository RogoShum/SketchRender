package rogo.sketchrender.shader.uniform;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.api.BufferObject;

public class SSBO implements BufferObject {
    private final int id;
    private boolean isDisposed = false;
    private long bufferPointer;
    private long capacity;
    private long dataCount;
    private final long stride;
    public int position;

    public SSBO(long dataCount, long stride, int usage) {
        this(dataCount, stride, MemoryUtil.nmemCalloc(dataCount, stride), usage);
    }

    public SSBO(long dataCount, long stride, long memoryAddress, int usage) {
        long totalCapacity = dataCount * stride;
        if (totalCapacity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Buffer capacity too large");
        }
        this.capacity = totalCapacity;
        this.stride = stride;
        this.dataCount = dataCount;
        bufferPointer = memoryAddress;
        id = GL15.glGenBuffers();
        if (id < 0) {
            throw new RuntimeException("Failed to create a new buffer");
        }
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferData(GL43.GL_SHADER_STORAGE_BUFFER, this.capacity, bufferPointer, usage);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public SSBO(BufferObject buffer) {
        this.capacity = buffer.getSize();
        this.bufferPointer = buffer.getMemoryAddress();
        this.id = buffer.getId();
        this.stride = buffer.getStride();
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferData(GL43.GL_SHADER_STORAGE_BUFFER, capacity, bufferPointer, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public int getId() {
        return id;
    }

    @Override
    public long getDataNum() {
        return dataCount;
    }

    @Override
    public long getSize() {
        return capacity;
    }

    @Override
    public long getMemoryAddress() {
        return bufferPointer;
    }

    public long getStride() {
        return stride;
    }

    public void bindShaderSlot(int bindingPoint) {
        checkDisposed();
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, bindingPoint, id);
    }

    public void upload() {
        checkDisposed();
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, position, bufferPointer);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void upload(long index) {
        checkDisposed();
        long indexOffset = index * getStride();
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, indexOffset, getStride(), bufferPointer + indexOffset);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void resetUpload(int usage) {
        checkDisposed();
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferData(GL43.GL_SHADER_STORAGE_BUFFER, capacity, bufferPointer, usage);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void ensureCapacity(int requiredCount) {
        checkDisposed();

        if (requiredCount * stride <= capacity) {
            return;
        }

        long newBufferPointer = MemoryUtil.nmemCalloc(requiredCount, stride);
        MemoryUtil.memCopy(bufferPointer, newBufferPointer, capacity);
        MemoryUtil.nmemFree(bufferPointer);
        this.bufferPointer = newBufferPointer;
        this.dataCount = requiredCount;
        this.capacity = requiredCount * stride;

        resetUpload(GL15.GL_DYNAMIC_DRAW);
    }

    public void setBufferPointer(long bufferPointer) {
        checkDisposed();
        this.bufferPointer = bufferPointer;
    }

    public void setCapacity(long capacity) {
        checkDisposed();
        this.capacity = capacity;
    }

    public void discard() {
        if (isDisposed) return;
        MemoryUtil.nmemFree(bufferPointer);
        GL15.glDeleteBuffers(id);
        isDisposed = true;
    }

    private void checkDisposed() {
        if (isDisposed) {
            throw new IllegalStateException("Buffer has been disposed");
        }
    }

    public void discardBufferId() {
        GL15.glDeleteBuffers(id);
    }

    public void discardMemory() {
        MemoryUtil.nmemFree(bufferPointer);
    }
}
