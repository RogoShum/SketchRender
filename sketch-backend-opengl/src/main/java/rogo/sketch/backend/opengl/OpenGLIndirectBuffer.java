package rogo.sketch.backend.opengl;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.api.DataResourceObject;
import rogo.sketch.core.backend.BackendIndirectBuffer;
import rogo.sketch.core.backend.BackendInstalledBuffer;

import java.nio.ByteBuffer;

public class OpenGLIndirectBuffer implements DataResourceObject, BackendInstalledBuffer, BackendIndirectBuffer {
    public static final int COMMAND_SIZE = 20; // 5 ints * 4 bytes
    private final int id;
    private final boolean glBacked;
    private long commandBuffer;
    private long iCapacity;
    private long commandCount;
    public int maxElementCount;
    public long position;
    protected boolean disposed = false;

    public OpenGLIndirectBuffer(long capacity) {
        glBacked = true;
        id = glBacked ? GL15.glGenBuffers() : 0;
        iCapacity = capacity * getStride();
        commandCount = capacity;
        commandBuffer = MemoryUtil.nmemCalloc(1, iCapacity);
        if (glBacked) {
            bind();
            GL15.nglBufferData(GL43.GL_DRAW_INDIRECT_BUFFER, iCapacity, commandBuffer, GL15.GL_STATIC_DRAW);
            unBind();
        }
    }

    public void resize(long capacity) {
        if (capacity <= commandCount) return;
        
        if (commandBuffer != 0) {
            MemoryUtil.nmemFree(this.commandBuffer);
        }
        
        iCapacity = capacity * getStride();
        commandCount = capacity;
        commandBuffer = MemoryUtil.nmemCalloc(1, iCapacity);
        
        if (glBacked) {
            bind();
            GL15.nglBufferData(GL43.GL_DRAW_INDIRECT_BUFFER, iCapacity, commandBuffer, GL15.GL_DYNAMIC_DRAW);
            unBind();
        }
    }

    @Override
    public void bind() {
        if (!glBacked) {
            return;
        }
        GL15.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, id);
    }

    @Override
    public void upload() {
        if (!glBacked) {
            return;
        }
        GL15.nglBufferSubData(GL43.GL_DRAW_INDIRECT_BUFFER, 0, position, commandBuffer);
    }
    
    /**
     * Upload data from a ByteBuffer
     */
    public void upload(ByteBuffer buffer, int count) {
        if (!glBacked) {
            return;
        }
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

    @Override
    public void clear() {
        position = 0;
        maxElementCount = 0;
    }

    public void dispose() {
        if (commandBuffer != 0) {
            MemoryUtil.nmemFree(this.commandBuffer);
            commandBuffer = 0;
        }
        if (glBacked) {
            GL15.nglDeleteBuffers(GL43.GL_DRAW_INDIRECT_BUFFER, id);
        }
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

    @Override
    public long strideBytes() {
        return getStride();
    }

    @Override
    public int commandCount() {
        return getCommandCount();
    }

    @Override
    public long writePositionBytes() {
        return position;
    }

    @Override
    public long memoryAddress() {
        return commandBuffer;
    }

    @Override
    public void unbind() {
        unBind();
    }

    public void addDrawCommand(int count, int instanceCount, int first, int baseInstance) {
        addDrawArraysCommand(count, instanceCount, first, baseInstance);
    }

    public void addDrawArraysCommand(int count, int instanceCount, int first, int baseInstance) {
        int index = ensureCommandCapacity();
        putArraysCommand(commandBuffer, index, count, instanceCount, first, baseInstance);
        position += COMMAND_SIZE;
    }

    public void addDrawElementsCommand(int count, int instanceCount, int firstIndex, int baseVertex, int baseInstance) {
        int index = ensureCommandCapacity();
        putElementsCommand(commandBuffer, index, count, instanceCount, firstIndex, baseVertex, baseInstance);
        position += COMMAND_SIZE;
    }

    private int ensureCommandCapacity() {
        if (position + COMMAND_SIZE > iCapacity) {
            resize(Math.max(commandCount * 2, 1));
        }
        return getCommandCount();
    }
}

