package rogo.sketchrender.shader;

import com.mojang.blaze3d.platform.GlStateManager;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.minecraft.core.BlockPos;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.api.BufferObject;
import rogo.sketchrender.shader.uniform.SSBO;

public class IndirectCommandBuffer implements BufferObject {
    public static final int REGION_COMMAND_SIZE = ModelQuadFacing.COUNT * 256 + 1;
    public static final IndirectCommandBuffer INSTANCE = new IndirectCommandBuffer(REGION_COMMAND_SIZE);
    private final int id = GL15.glGenBuffers();
    private int chunkX;
    private int chunkY;
    private int chunkZ;
    private int regionX;
    private int regionY;
    private int regionZ;
    private long commandBuffer;
    private int iCapacity;
    private int commandCount;
    public int maxElementCount;
    public int position;

    public IndirectCommandBuffer(int capacity) {
        iCapacity = capacity * 20;
        commandCount = capacity;
        commandBuffer = MemoryUtil.nmemCalloc(1, iCapacity);
        GL15.nglBufferData(GL43.GL_DRAW_INDIRECT_BUFFER, iCapacity, commandBuffer, GL15.GL_DYNAMIC_DRAW);
    }

    public void resize(int capacity) {
        iCapacity = capacity * 20;
        commandCount = capacity;
        commandBuffer = MemoryUtil.nmemCalloc(1, iCapacity);
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

    public int getCommandCount() {
        return commandCount;
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
        MemoryUtil.memPutInt(ssbo.getMemoryAddress() + offset, chunkX);
        MemoryUtil.memPutInt(ssbo.getMemoryAddress() + offset + 4, chunkY);
        MemoryUtil.memPutInt(ssbo.getMemoryAddress() + offset + 8, chunkZ);
        MemoryUtil.memPutInt(ssbo.getMemoryAddress() + offset + 12, elementCount);
        MemoryUtil.memPutInt(ssbo.getMemoryAddress() + offset + 16, vertexOffset);
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

    public void switchRegion(int x, int y, int z) {
        regionX = x;
        regionY = y;
        regionZ = z;
    }

    public BlockPos getRegionPos() {
        return new BlockPos(regionX, regionY, regionZ);
    }

    public void delete() {
        MemoryUtil.nmemFree(this.commandBuffer);
        GL15.nglDeleteBuffers(GL43.GL_DRAW_INDIRECT_BUFFER, id);
    }
}
