package rogo.sketch.backend.vulkan;

import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.backend.BackendIndirectBuffer;

final class VulkanIndirectBufferResource implements BackendIndirectBuffer {
    private long commandBuffer;
    private long capacityBytes;
    private long positionBytes;
    private int commandCount;
    private boolean disposed;

    VulkanIndirectBufferResource(long commandCapacity) {
        long capacity = Math.max(1L, commandCapacity);
        this.capacityBytes = capacity * COMMAND_STRIDE_BYTES;
        this.commandBuffer = MemoryUtil.nmemCalloc(1L, capacityBytes);
    }

    @Override
    public long strideBytes() {
        return COMMAND_STRIDE_BYTES;
    }

    @Override
    public int commandCount() {
        return commandCount;
    }

    @Override
    public long writePositionBytes() {
        return positionBytes;
    }

    @Override
    public long memoryAddress() {
        return commandBuffer;
    }

    @Override
    public void clear() {
        positionBytes = 0L;
        commandCount = 0;
    }

    @Override
    public void bind() {
        // Vulkan command buffers bind indirect buffers through arena-owned native slices.
    }

    @Override
    public void unbind() {
        // No-op for Vulkan CPU staging indirect buffer.
    }

    @Override
    public void upload() {
        // Vulkan uploads staged command bytes through geometry arena snapshots.
    }

    @Override
    public void addDrawArraysCommand(int count, int instanceCount, int first, int baseInstance) {
        int index = ensureCommandCapacity();
        putArraysCommand(commandBuffer, index, count, instanceCount, first, baseInstance);
        positionBytes += COMMAND_STRIDE_BYTES;
        commandCount++;
    }

    @Override
    public void addDrawElementsCommand(int count, int instanceCount, int firstIndex, int baseVertex, int baseInstance) {
        int index = ensureCommandCapacity();
        putElementsCommand(commandBuffer, index, count, instanceCount, firstIndex, baseVertex, baseInstance);
        positionBytes += COMMAND_STRIDE_BYTES;
        commandCount++;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (commandBuffer != MemoryUtil.NULL) {
            MemoryUtil.nmemFree(commandBuffer);
            commandBuffer = MemoryUtil.NULL;
        }
        capacityBytes = 0L;
        positionBytes = 0L;
        commandCount = 0;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private int ensureCommandCapacity() {
        if (positionBytes + COMMAND_STRIDE_BYTES <= capacityBytes) {
            return commandCount;
        }
        long previousCapacity = capacityBytes;
        long newCapacity = Math.max(previousCapacity * 2L, COMMAND_STRIDE_BYTES);
        long newBuffer = MemoryUtil.nmemCalloc(1L, newCapacity);
        if (commandBuffer != MemoryUtil.NULL && positionBytes > 0L) {
            MemoryUtil.memCopy(commandBuffer, newBuffer, positionBytes);
            MemoryUtil.nmemFree(commandBuffer);
        }
        commandBuffer = newBuffer;
        capacityBytes = newCapacity;
        return commandCount;
    }

    private static void putArraysCommand(long address, int index, int count, int instanceCount, int first, int baseInstance) {
        long offset = address + (long) index * COMMAND_STRIDE_BYTES;
        MemoryUtil.memPutInt(offset + 0, count);
        MemoryUtil.memPutInt(offset + 4, instanceCount);
        MemoryUtil.memPutInt(offset + 8, first);
        MemoryUtil.memPutInt(offset + 12, baseInstance);
    }

    private static void putElementsCommand(long address, int index, int count, int instanceCount, int firstIndex, int baseVertex, int baseInstance) {
        long offset = address + (long) index * COMMAND_STRIDE_BYTES;
        MemoryUtil.memPutInt(offset + 0, count);
        MemoryUtil.memPutInt(offset + 4, instanceCount);
        MemoryUtil.memPutInt(offset + 8, firstIndex);
        MemoryUtil.memPutInt(offset + 12, baseVertex);
        MemoryUtil.memPutInt(offset + 16, baseInstance);
    }
}

