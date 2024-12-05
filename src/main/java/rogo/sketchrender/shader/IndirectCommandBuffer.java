package rogo.sketchrender.shader;

import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;

public class IndirectCommandBuffer {
    private final int id = GL15.glGenBuffers();
    public static final IndirectCommandBuffer INSTANCE = new IndirectCommandBuffer(ModelQuadFacing.COUNT * 256 + 1);
    private boolean bind;
    public final long commandBuffer;
    public final int iCapacity;
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
    }

    public void bind() {
        GL15.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, id);
    }

    public void bindOnce() {
        if (!bind) {
            GL15.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, id);
        }
    }

    public void upload() {
        GL15.nglBufferData(GL43.GL_DRAW_INDIRECT_BUFFER, position, commandBuffer, GL15.GL_DYNAMIC_DRAW);
    }

    public int getId() {
        return id;
    }

    public long getBufferAddress() {
        return commandBuffer;
    }

    public void unBind() {
        GL15.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, 0);
    }

    public void putChunkData(int size, int elementCount, int vertexOffset) {
        maxElementCount = Math.max(elementCount, maxElementCount);

        int offset = size * 20;
        MemoryUtil.memPutInt(commandBuffer + offset, elementCount);
        MemoryUtil.memPutInt(commandBuffer + offset + 12, vertexOffset);
        position = offset + 20;
    }

    public void clear() {
        position = 0;
        maxElementCount = 0;
    }

    public void delete() {
        MemoryUtil.nmemAlignedFree(this.commandBuffer);
        GL15.nglDeleteBuffers(GL43.GL_DRAW_INDIRECT_BUFFER, id);
    }
}
