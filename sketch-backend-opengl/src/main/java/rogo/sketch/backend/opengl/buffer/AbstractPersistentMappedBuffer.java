package rogo.sketch.backend.opengl.buffer;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.api.DataResourceObject;
import rogo.sketch.core.memory.MemoryDomain;
import rogo.sketch.core.memory.MemoryLease;
import rogo.sketch.core.memory.UnifiedMemoryFabric;

import java.nio.ByteBuffer;

/**
 * Base class for persistent mapped GL buffers.
 */
public abstract class AbstractPersistentMappedBuffer implements DataResourceObject, AutoCloseable {
    protected int id;
    protected final int target;
    protected final long stride;
    protected long capacity;
    protected long dataCount;
    protected long mappedAddress;
    protected boolean disposed = false;
    protected ByteBuffer mappedBuffer;
    protected final int storageFlags;
    protected final int mapFlags;
    private int ringSlots = 1;
    private int currentSlot = 0;
    private long slotSize = 0L;
    private long[] slotFences = null;
    private final MemoryLease memoryLease;

    protected AbstractPersistentMappedBuffer(int target, long dataCount, long stride, int storageFlags, int mapFlags) {
        this.target = target;
        this.dataCount = dataCount;
        this.stride = stride;
        this.storageFlags = storageFlags;
        this.mapFlags = mapFlags;
        allocate(dataCount);
        this.memoryLease = UnifiedMemoryFabric.get()
                .openLease(MemoryDomain.GPU_PERSISTENT_MAPPED, "gl-persistent-mapped-buffer")
                .bindSuppliers(this::trackedCapacityBytes, this::trackedLiveBytes);
        configureRingSlots(1);
    }

    private void allocate(long count) {
        this.capacity = count * stride;
        this.id = GL15.glGenBuffers();
        if (id <= 0) {
            throw new IllegalStateException("Failed to create persistent buffer");
        }

        long initPtr = MemoryUtil.nmemCalloc(count, stride);
        GL15.glBindBuffer(target, id);
        GL45.nglBufferStorage(target, capacity, initPtr, storageFlags);
        mappedBuffer = GL45.glMapBufferRange(target, 0, capacity, mapFlags);
        GL15.glBindBuffer(target, 0);
        MemoryUtil.nmemFree(initPtr);

        if (mappedBuffer == null) {
            throw new IllegalStateException("Failed to map persistent buffer");
        }
        mappedAddress = MemoryUtil.memAddress(mappedBuffer);
    }

    public void ensureCapacity(long requiredCount, boolean force) {
        checkDisposed();
        long requiredCapacity = requiredCount * stride;
        if (!force && requiredCapacity <= capacity) {
            return;
        }

        GL15.glBindBuffer(target, id);
        GL45.glUnmapBuffer(target);
        GL15.glBindBuffer(target, 0);
        GL15.glDeleteBuffers(id);

        this.dataCount = requiredCount;
        allocate(requiredCount);
    }

    public void ensureCapacity(long requiredCount) {
        ensureCapacity(requiredCount, false);
    }

    public void flushRange(long offset, long length) {
        checkDisposed();
        GL45.glFlushMappedBufferRange(target, offset, length);
    }

    public void barrier(int barrierBits) {
        checkDisposed();
        GL45.glMemoryBarrier(barrierBits);
    }

    public ByteBuffer getMappedBuffer() {
        checkDisposed();
        return mappedBuffer;
    }

    public void configureRingSlots(int slots) {
        checkDisposed();
        if (slots < 1) {
            throw new IllegalArgumentException("slots must be >= 1");
        }
        if (slotFences != null) {
            for (long fence : slotFences) {
                if (fence != 0L) {
                    GL32.glDeleteSync(fence);
                }
            }
        }
        this.ringSlots = slots;
        this.currentSlot = 0;
        this.slotSize = Math.max(stride, capacity / slots);
        this.slotFences = new long[slots];
    }

    /**
     * Acquire next writable ring slot and returns byte offset into mapped memory.
     */
    public long acquireWriteOffset() {
        checkDisposed();
        if (ringSlots <= 1) {
            return 0L;
        }
        currentSlot = (currentSlot + 1) % ringSlots;
        waitSlot(currentSlot);
        return currentSlot * slotSize;
    }

    public long getSlotSize() {
        return slotSize;
    }

    public void signalSlotSubmitted() {
        checkDisposed();
        if (slotFences == null || slotFences.length == 0) {
            return;
        }
        if (slotFences[currentSlot] != 0L) {
            GL32.glDeleteSync(slotFences[currentSlot]);
        }
        slotFences[currentSlot] = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    private void waitSlot(int slot) {
        if (slotFences == null || slot < 0 || slot >= slotFences.length) {
            return;
        }
        long fence = slotFences[slot];
        if (fence == 0L) {
            return;
        }
        GL32.glClientWaitSync(fence, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000L);
        GL32.glDeleteSync(fence);
        slotFences[slot] = 0L;
    }

    @Override
    public int getHandle() {
        return id;
    }

    @Override
    public long getDataCount() {
        return dataCount;
    }

    @Override
    public long getCapacity() {
        return capacity;
    }

    @Override
    public long getMemoryAddress() {
        return mappedAddress;
    }

    @Override
    public long getStride() {
        return stride;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        if (slotFences != null) {
            for (long fence : slotFences) {
                if (fence != 0L) {
                    GL32.glDeleteSync(fence);
                }
            }
            slotFences = null;
        }
        GL15.glBindBuffer(target, id);
        GL45.glUnmapBuffer(target);
        GL15.glBindBuffer(target, 0);
        GL15.glDeleteBuffers(id);
        mappedBuffer = null;
        mappedAddress = 0L;
        disposed = true;
        memoryLease.close();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    protected void checkDisposed() {
        if (disposed) {
            throw new IllegalStateException("Buffer has been disposed");
        }
    }

    @Override
    public void close() {
        dispose();
    }

    private long trackedCapacityBytes() {
        return disposed ? 0L : capacity;
    }

    private long trackedLiveBytes() {
        return disposed ? 0L : capacity;
    }
}



