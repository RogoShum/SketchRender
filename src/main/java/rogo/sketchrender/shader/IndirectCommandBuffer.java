package rogo.sketchrender.shader;

import com.mojang.blaze3d.platform.GlStateManager;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.minecraft.client.renderer.texture.SpriteLoader;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.api.BufferObject;
import rogo.sketchrender.shader.uniform.SSBO;

public class IndirectCommandBuffer implements BufferObject {
    public static final IndirectCommandBuffer INSTANCE = new IndirectCommandBuffer(ModelQuadFacing.COUNT * 256 + 1);
    private final int id = GL15.glGenBuffers();
    private int chunkX;
    private int chunkY;
    private int chunkZ;
    private final long commandBuffer;
    private final int iCapacity;
    public int maxElementCount;
    public int position;

    public IndirectCommandBuffer(int capacity) {
        iCapacity = capacity * 20;
        commandBuffer = MemoryUtil.nmemAlignedAlloc(32L, iCapacity);
        for (int i = 0; i < capacity; ++i) {
            int offset = i * 20;
            MemoryUtil.memPutInt(commandBuffer + offset + 4, 1);
            MemoryUtil.memPutInt(commandBuffer + offset + 8, 0);
            MemoryUtil.memPutInt(commandBuffer + offset + 16, 0);
        }

        GL15.nglBufferData(GL43.GL_DRAW_INDIRECT_BUFFER, iCapacity, commandBuffer, GL15.GL_DYNAMIC_DRAW);
    }

    public void bind() {
        GlStateManager._glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, id);
    }

    public void upload() {
        GL15.nglBufferSubData(GL43.GL_DRAW_INDIRECT_BUFFER, 0, position, commandBuffer);
    }

    public int getId() {
        return id;
    }

    @Override
    public int getSize() {
        return iCapacity;
    }

    public long getMemoryAddress() {
        return commandBuffer;
    }

    public void unBind() {
        GlStateManager._glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, 0);
    }

    public void putChunkData(int size, int elementCount, int vertexOffset) {
        maxElementCount = Math.max(elementCount, maxElementCount);

        int offset = size * 20;
        MemoryUtil.memPutInt(commandBuffer + offset, elementCount);
        MemoryUtil.memPutInt(commandBuffer + offset + 12, vertexOffset);
        position = offset + 20;
    }

    public void putSSBOData(SSBO ssbo, int size, int elementCount, int vertexOffset) {
        maxElementCount = Math.max(elementCount, maxElementCount);

        int offset = size * 20;
        MemoryUtil.memPutInt(ssbo.bufferPointer + offset, chunkX);
        MemoryUtil.memPutInt(ssbo.bufferPointer + offset + 4, chunkY);
        MemoryUtil.memPutInt(ssbo.bufferPointer + offset + 8, chunkZ);
        MemoryUtil.memPutInt(ssbo.bufferPointer + offset + 12, elementCount);
        MemoryUtil.memPutInt(ssbo.bufferPointer + offset + 16, vertexOffset);
        ssbo.position = offset + 20;
    }

    public void clear() {
        position = 0;
        maxElementCount = 0;
    }

    public void switchSection(int x, int y, int z) {
        chunkX = x;
        chunkY = y;
        chunkZ = z;
    }

    public void delete() {
        MemoryUtil.nmemAlignedFree(this.commandBuffer);
        GL15.nglDeleteBuffers(GL43.GL_DRAW_INDIRECT_BUFFER, id);
    }
}
