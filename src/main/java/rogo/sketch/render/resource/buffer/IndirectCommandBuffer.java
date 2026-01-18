package rogo.sketch.render.resource.buffer;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.api.DataResourceObject;

import java.nio.ByteBuffer;

public class IndirectCommandBuffer implements DataResourceObject {
    public static final int COMMAND_SIZE = 20; // 5 ints * 4 bytes
    private final int id = GL15.glGenBuffers();
    private long commandBuffer;
    private long iCapacity;
    private long commandCount;
    public int maxElementCount;
    public long position;
    protected boolean disposed = false;

    public IndirectCommandBuffer(long capacity) {
        iCapacity = capacity * getStride();
        commandCount = capacity;
        commandBuffer = MemoryUtil.nmemCalloc(1, iCapacity);
        bind();
        GL15.nglBufferData(GL43.GL_DRAW_INDIRECT_BUFFER, iCapacity, commandBuffer, GL15.GL_STATIC_DRAW);
        unBind();
    }

    public void resize(long capacity) {
        if (capacity <= commandCount) return;
        
        if (commandBuffer != 0) {
            MemoryUtil.nmemFree(this.commandBuffer);
        }
        
        iCapacity = capacity * getStride();
        commandCount = capacity;
        commandBuffer = MemoryUtil.nmemCalloc(1, iCapacity);
        
        bind();
        GL15.nglBufferData(GL43.GL_DRAW_INDIRECT_BUFFER, iCapacity, commandBuffer, GL15.GL_DYNAMIC_DRAW);
        unBind();
    }

    public void bind() {
        GL15.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, id);
    }

    public void upload() {
        GL15.nglBufferSubData(GL43.GL_DRAW_INDIRECT_BUFFER, 0, position, commandBuffer);
    }
    
    /**
     * Upload data from a ByteBuffer
     */
    public void upload(ByteBuffer buffer, int count) {
        long size = (long) count * COMMAND_SIZE;
        bind();
        GL15.glBufferSubData(GL43.GL_DRAW_INDIRECT_BUFFER, 0, buffer);
        unBind();
    }

    public int getHandle() {
        return id;
    }

    @Override
    public long getDataCount() {
        return commandCount;
    }

    @Override
    public long getCapacity() {
        return iCapacity;
    }

    @Override
    public long getStride() {
        return COMMAND_SIZE;
    }

    public long getMemoryAddress() {
        return commandBuffer;
    }

    public static void unBind() {
        GL15.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, 0);
    }

    public void clear() {
        position = 0;
        maxElementCount = 0;
    }

    public void dispose() {
        if (commandBuffer != 0) {
            MemoryUtil.nmemFree(this.commandBuffer);
            commandBuffer = 0;
        }
        GL15.nglDeleteBuffers(GL43.GL_DRAW_INDIRECT_BUFFER, id);
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
    
    // === Static Helpers for writing commands ===
    
    public static void putArraysCommand(long address, int index, int count, int instanceCount, int first, int baseInstance) {
        long offset = address + (long) index * COMMAND_SIZE;
        MemoryUtil.memPutInt(offset + 0, count);
        MemoryUtil.memPutInt(offset + 4, instanceCount);
        MemoryUtil.memPutInt(offset + 8, first);
        MemoryUtil.memPutInt(offset + 12, baseInstance);
    }
    
    public static void putElementsCommand(long address, int index, int count, int instanceCount, int firstIndex, int baseVertex, int baseInstance) {
        long offset = address + (long) index * COMMAND_SIZE;
        MemoryUtil.memPutInt(offset + 0, count);
        MemoryUtil.memPutInt(offset + 4, instanceCount);
        MemoryUtil.memPutInt(offset + 8, firstIndex);
        MemoryUtil.memPutInt(offset + 12, baseVertex);
        MemoryUtil.memPutInt(offset + 16, baseInstance);
    }

    // === Dynamic Command Building ===

    public int getCommandCount() {
        return (int) (position / COMMAND_SIZE);
    }

    public void addDrawCommand(int count, int instanceCount, int first, int baseInstance) {
        // Ensure capacity
        if (position + COMMAND_SIZE > iCapacity) {
            resize(commandCount * 2);
        }

        // Write directly to native memory or buffer
        long addr = commandBuffer + position;

        // DrawArraysIndirectCommand: { count, instanceCount, first, baseInstance }
        // We write 5 ints (stride 20) to stay compatible with standard indirect buffers layout in this engine
        MemoryUtil.memPutInt(addr + 0, count);
        MemoryUtil.memPutInt(addr + 4, instanceCount);
        MemoryUtil.memPutInt(addr + 8, first);
        MemoryUtil.memPutInt(addr + 12, 0); // Reserved/BaseVertex
        MemoryUtil.memPutInt(addr + 16, baseInstance);

        position += COMMAND_SIZE;
    }
}