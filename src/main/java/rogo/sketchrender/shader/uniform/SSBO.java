package rogo.sketchrender.shader.uniform;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.shader.IndirectCommandBuffer;

import java.nio.IntBuffer;

public class SSBO {
    private final int id;
    public final long bufferPointer;
    public final int iCapacity;
    public int position;

    public SSBO(int capacity) {
        iCapacity = capacity * 20 + 4;
        bufferPointer = MemoryUtil.nmemAlignedAlloc(32L, iCapacity);
        id = GL15.glGenBuffers();
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferData(GL43.GL_SHADER_STORAGE_BUFFER, iCapacity, bufferPointer, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public SSBO(IndirectCommandBuffer buffer) {
        iCapacity = buffer.iCapacity;
        bufferPointer = buffer.commandBuffer;
        id = buffer.getId();
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferData(GL43.GL_SHADER_STORAGE_BUFFER, iCapacity, bufferPointer, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public int getId() {
        return id;
    }

    public void bind(int bindingPoint) {
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, bindingPoint, id);
    }

    public void upload() {
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferData(GL43.GL_SHADER_STORAGE_BUFFER, position, bufferPointer, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public int[] getData(int count) {
        IntBuffer buffer = BufferUtils.createIntBuffer(count);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL43.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, buffer);
        int[] data = new int[count];
        buffer.get(data);
        MemoryUtil.memFree(buffer);
        return data;
    }

    public void discard() {
        GL15.glDeleteBuffers(id);
    }
}
