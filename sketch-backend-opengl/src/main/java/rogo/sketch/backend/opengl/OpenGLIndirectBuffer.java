package rogo.sketch.backend.opengl;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.api.DataResourceObject;
import rogo.sketch.core.backend.BackendIndirectBuffer;
import rogo.sketch.core.backend.BackendInstalledBuffer;
import rogo.sketch.core.memory.MemoryDomain;
import rogo.sketch.core.memory.MemoryLease;
import rogo.sketch.core.memory.UnifiedMemoryFabric;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.module.culling.TerrainMeshIndirectBuffer;

import java.nio.ByteBuffer;

public class OpenGLIndirectBuffer implements DataResourceObject, BackendInstalledBuffer, BackendIndirectBuffer,
        TerrainMeshIndirectBuffer {
    public static final int COMMAND_SIZE = 20; // 5 ints * 4 bytes
    private final int id;
    private final boolean glBacked;
    private long commandBuffer;
    private long iCapacity;
    private long commandCapacity;
    private int writtenCommandCount;
    public int maxElementCount;
    public long position;
    private long dirtyStart = Long.MAX_VALUE;
    private long dirtyEnd = 0L;
    protected boolean disposed = false;
    private final MemoryLease cpuStagingLease;
    private final MemoryLease gpuIndirectLease;

    public OpenGLIndirectBuffer(long capacity) {
        glBacked = true;
        id = glBacked ? GL15.glGenBuffers() : 0;
        iCapacity = capacity * getStride();
        commandCapacity = capacity;
        writtenCommandCount = 0;
        commandBuffer = MemoryUtil.nmemCalloc(1, iCapacity);
        cpuStagingLease = UnifiedMemoryFabric.get()
                .openLease(MemoryDomain.CPU_INDIRECT_STAGING, "gl-indirect-buffer/cpu-staging")
                .bindSuppliers(this::trackedCpuReservedBytes, this::trackedCpuLiveBytes);
        gpuIndirectLease = UnifiedMemoryFabric.get()
                .openLease(MemoryDomain.GPU_INDIRECT_BUFFER, "gl-indirect-buffer/gpu")
                .bindSuppliers(this::trackedGpuReservedBytes, this::trackedGpuLiveBytes);
        if (glBacked) {
            bind();
            GL15.nglBufferData(GL43.GL_DRAW_INDIRECT_BUFFER, iCapacity, commandBuffer, GL15.GL_STATIC_DRAW);
            unBind();
        }
    }

    public void resize(long capacity) {
        if (capacity <= commandCapacity) return;

        long previousCapacityBytes = iCapacity;
        long previousBuffer = commandBuffer;
        
        iCapacity = capacity * getStride();
        commandCapacity = capacity;
        commandBuffer = MemoryUtil.nmemCalloc(1, iCapacity);
        if (previousBuffer != 0 && position > 0L) {
            MemoryUtil.memCopy(previousBuffer, commandBuffer, Math.min(position, previousCapacityBytes));
        }
        if (previousBuffer != 0) {
            MemoryUtil.nmemFree(previousBuffer);
        }
        
        if (glBacked) {
            bind();
            GL15.nglBufferData(GL43.GL_DRAW_INDIRECT_BUFFER, iCapacity, commandBuffer, GL15.GL_DYNAMIC_DRAW);
            if (position > 0L) {
                markDirtyRange(0L, position);
                upload();
            }
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
    public void bind(KeyId resourceType, int binding) {
        if (!glBacked) {
            return;
        }
        KeyId normalizedType = ResourceTypes.normalize(resourceType);
        if (ResourceTypes.STORAGE_BUFFER.equals(normalizedType)) {
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, id);
            return;
        }
        bind();
    }

    @Override
    public void upload() {
        if (!glBacked) {
            return;
        }
        long uploadOffset = dirtyStart != Long.MAX_VALUE ? dirtyStart : 0L;
        long uploadSize = dirtyStart != Long.MAX_VALUE ? Math.max(0L, dirtyEnd - dirtyStart) : position;
        if (uploadSize <= 0L) {
            return;
        }
        bind();
        GL15.nglBufferSubData(GL43.GL_DRAW_INDIRECT_BUFFER, uploadOffset, uploadSize, commandBuffer + uploadOffset);
        unBind();
        clearDirtyRange();
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
        return commandCapacity;
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
        writtenCommandCount = 0;
        maxElementCount = 0;
        clearDirtyRange();
    }

    public void dispose() {
        if (commandBuffer != 0) {
            MemoryUtil.nmemFree(this.commandBuffer);
            commandBuffer = 0;
        }
        if (glBacked) {
            GL15.glDeleteBuffers(id);
        }
        disposed = true;
        cpuStagingLease.close();
        gpuIndirectLease.close();
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
        return writtenCommandCount;
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
    public void ensureCommandCapacity(int requiredCommandCount) {
        if (requiredCommandCount <= 0) {
            return;
        }
        resize(requiredCommandCount);
    }

    @Override
    public void uploadRange(long byteOffset, long byteCount) {
        if (byteCount <= 0L) {
            return;
        }
        markDirtyRange(byteOffset, byteOffset + byteCount);
    }

    @Override
    public void setCommandCount(int commandCount) {
        this.writtenCommandCount = Math.max(commandCount, 0);
    }

    @Override
    public void setWritePositionBytes(long byteCount) {
        this.position = Math.max(byteCount, 0L);
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
        writtenCommandCount++;
        markDirtyRange((long) index * COMMAND_SIZE, position);
    }

    public void addDrawElementsCommand(int count, int instanceCount, int firstIndex, int baseVertex, int baseInstance) {
        int index = ensureCommandCapacity();
        putElementsCommand(commandBuffer, index, count, instanceCount, firstIndex, baseVertex, baseInstance);
        position += COMMAND_SIZE;
        writtenCommandCount++;
        markDirtyRange((long) index * COMMAND_SIZE, position);
    }

    private int ensureCommandCapacity() {
        if (position + COMMAND_SIZE > iCapacity) {
            resize(Math.max(commandCapacity * 2, 1));
        }
        return writtenCommandCount;
    }

    private void markDirtyRange(long start, long end) {
        dirtyStart = Math.min(dirtyStart, Math.max(0L, start));
        dirtyEnd = Math.max(dirtyEnd, Math.max(start, end));
    }

    private void clearDirtyRange() {
        dirtyStart = Long.MAX_VALUE;
        dirtyEnd = 0L;
    }

    private long trackedCpuReservedBytes() {
        return disposed ? 0L : iCapacity;
    }

    private long trackedCpuLiveBytes() {
        return disposed ? 0L : Math.max(0L, position);
    }

    private long trackedGpuReservedBytes() {
        return glBacked && !disposed ? iCapacity : 0L;
    }

    private long trackedGpuLiveBytes() {
        return glBacked && !disposed ? Math.max(0L, position) : 0L;
    }
}

