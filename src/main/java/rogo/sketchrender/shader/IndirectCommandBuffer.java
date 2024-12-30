package rogo.sketchrender.shader;

import com.mojang.blaze3d.platform.GlStateManager;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.api.BufferObject;

public class IndirectCommandBuffer implements BufferObject {
    public static final int REGION_COMMAND_SIZE = ModelQuadFacing.COUNT * 256 + 1;
    public static final int PASS_SIZE = 3;
    public static final int REGION_PASS_COMMAND_SIZE = REGION_COMMAND_SIZE * PASS_SIZE;
    public static final IndirectCommandBuffer INSTANCE = new IndirectCommandBuffer(REGION_COMMAND_SIZE);
    private final int id = GL15.glGenBuffers();
    private long commandBuffer;
    private long iCapacity;
    private int commandCount;
    public int maxElementCount;
    public int position;

    public IndirectCommandBuffer(int capacity) {
        iCapacity = capacity * getStride();
        commandCount = capacity;
        commandBuffer = MemoryUtil.nmemCalloc(1, iCapacity);
        bind();
        GL15.nglBufferData(GL43.GL_DRAW_INDIRECT_BUFFER, iCapacity, commandBuffer, GL15.GL_STATIC_DRAW);
        unBind();
    }

    public void resize(int capacity) {
        iCapacity = capacity * getStride();
        commandCount = capacity;
        MemoryUtil.nmemFree(this.commandBuffer);
        commandBuffer = MemoryUtil.nmemCalloc(1, iCapacity);
        bind();
        GL15.nglBufferData(GL43.GL_DRAW_INDIRECT_BUFFER, iCapacity, commandBuffer, GL15.GL_STATIC_DRAW);
        unBind();
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
    public long getDataNum() {
        return commandCount;
    }

    @Override
    public long getSize() {
        return iCapacity;
    }

    @Override
    public long getStride() {
        return 20L;
    }

    public long getMemoryAddress() {
        return commandBuffer;
    }

    public void unBind() {
        GlStateManager._glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, 0);
    }

    public void clear() {
        position = 0;
        maxElementCount = 0;
    }

    public void delete() {
        MemoryUtil.nmemFree(this.commandBuffer);
        GL15.nglDeleteBuffers(GL43.GL_DRAW_INDIRECT_BUFFER, id);
    }
}
