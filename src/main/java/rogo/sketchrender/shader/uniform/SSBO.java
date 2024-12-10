package rogo.sketchrender.shader.uniform;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.api.BufferObject;

public class SSBO implements BufferObject{
    private final int id;
    private long bufferPointer;
    private int capacity;
    private int stride;
    public int position;

    public SSBO(int capacity, int stride) {
        this.capacity = capacity * stride;
        this.stride = stride;
        bufferPointer = MemoryUtil.nmemCalloc(capacity, stride);
        id = GL15.glGenBuffers();
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferData(GL43.GL_SHADER_STORAGE_BUFFER, this.capacity, bufferPointer, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public SSBO(BufferObject buffer) {
        capacity = buffer.getSize();
        bufferPointer = buffer.getMemoryAddress();
        id = buffer.getId();
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferData(GL43.GL_SHADER_STORAGE_BUFFER, capacity, bufferPointer, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public int getId() {
        return id;
    }

    @Override
    public int getSize() {
        return capacity;
    }

    @Override
    public long getMemoryAddress() {
        return bufferPointer;
    }

    public int getStride() {
        return stride;
    }

    public void bindShaderSlot(int bindingPoint) {
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, bindingPoint, id);
    }

    public void upload() {
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, position, bufferPointer);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void upload(long index) {
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, index * getStride(), getStride(), bufferPointer);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void resetUpload() {
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferData(GL43.GL_SHADER_STORAGE_BUFFER, capacity, bufferPointer, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity * stride <= capacity) {
            return;
        }

        long newBufferPointer = MemoryUtil.nmemAlignedAlloc(32L, (long) requiredCapacity * stride);
        MemoryUtil.memCopy(bufferPointer, newBufferPointer, capacity);

        MemoryUtil.nmemFree(bufferPointer);

        bufferPointer = newBufferPointer;
        capacity = requiredCapacity * stride;
    }

    public void discard() {
        GL15.glDeleteBuffers(id);
    }
}
