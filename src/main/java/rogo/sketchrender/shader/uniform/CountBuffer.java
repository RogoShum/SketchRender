package rogo.sketchrender.shader.uniform;

import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL46;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.api.BufferObject;

public class CountBuffer implements BufferObject {
    private long bufferPointer;
    private final int bufferId;
    private int size;
    private final VertexFormatElement.Type counterType;

    public CountBuffer(VertexFormatElement.Type type) {
        counterType = type;
        bufferPointer = MemoryUtil.nmemAlignedAlloc(32L, type.getSize());
        MemoryUtil.memSet(bufferPointer, 0, type.getSize());
        this.size = type.getSize();

        bufferId = GL33.glGenBuffers();
        GL33.glBindBuffer(GL46.GL_PARAMETER_BUFFER, bufferId);
        GL15C.nglBufferData(GL46.GL_PARAMETER_BUFFER, size, bufferPointer, GL15.GL_DYNAMIC_DRAW);
        GL33.glBindBuffer(GL46.GL_PARAMETER_BUFFER, 0);
    }

    public void resize(int count) {
        this.size = getCounterStride() * count;
        MemoryUtil.nmemFree(bufferPointer);
        bufferPointer = MemoryUtil.nmemAlignedAlloc(32L, this.size);
        MemoryUtil.memSet(bufferPointer, 0, this.size);
        GL33.glBindBuffer(GL46.GL_PARAMETER_BUFFER, bufferId);
        GL15C.nglBufferData(GL46.GL_PARAMETER_BUFFER, size, bufferPointer, GL15.GL_DYNAMIC_DRAW);
        GL33.glBindBuffer(GL46.GL_PARAMETER_BUFFER, 0);
    }

    public int getId() {
        return bufferId;
    }

    public int getSize() {
        return size;
    }

    public int getCounterStride() {
        return counterType.getSize();
    }

    public long getMemoryAddress() {
        return bufferPointer;
    }

    public void bind() {
        GL33.glBindBuffer(GL46.GL_PARAMETER_BUFFER, bufferId);
    }

    public void updateCount(int count) {
        MemoryUtil.memSet(bufferPointer, count, this.size);
        bind();
        GL15C.nglBufferSubData(GL46.GL_PARAMETER_BUFFER, 0, size, bufferPointer);
    }

    public void cleanup() {
        GL33.glDeleteBuffers(bufferId);
        MemoryUtil.nmemFree(bufferPointer);
    }
}
