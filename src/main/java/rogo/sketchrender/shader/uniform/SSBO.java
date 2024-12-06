package rogo.sketchrender.shader.uniform;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.shader.IndirectCommandBuffer;

public class SSBO {
    private final int id;
    public final long bufferPointer;
    public final int iCapacity;
    public int position;

    public SSBO(int capacity) {
        iCapacity = capacity * 20;
        bufferPointer = MemoryUtil.nmemAlignedAlloc(32L, iCapacity);
        id = GL15.glGenBuffers();
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferData(GL43.GL_SHADER_STORAGE_BUFFER, iCapacity, bufferPointer, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public SSBO(IndirectCommandBuffer buffer) {
        iCapacity = buffer.iCapacity;
        bufferPointer = buffer.getMemoryAddress();
        id = buffer.getId();
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferData(GL43.GL_SHADER_STORAGE_BUFFER, iCapacity, bufferPointer, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public int getId() {
        return id;
    }

    public void bindShaderSlot(int bindingPoint) {
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, bindingPoint, id);
    }

    public void upload() {
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.nglBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, position, bufferPointer);
        GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void discard() {
        GL15.glDeleteBuffers(id);
    }
}
