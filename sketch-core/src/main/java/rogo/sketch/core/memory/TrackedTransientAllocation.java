package rogo.sketch.core.memory;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class TrackedTransientAllocation implements AutoCloseable {
    private final ByteBuffer buffer;
    private final MemoryLease lease;

    private TrackedTransientAllocation(ByteBuffer buffer, MemoryLease lease) {
        this.buffer = buffer;
        this.lease = lease;
    }

    public static TrackedTransientAllocation allocate(String ownerId, int byteCount) {
        int size = Math.max(1, byteCount);
        ByteBuffer buffer = MemoryUtil.memAlloc(size);
        MemoryLease lease = UnifiedMemoryFabric.get()
                .openLease(MemoryDomain.FRAME_TRANSIENT, ownerId != null ? ownerId : "frame-transient")
                .update(size, size);
        return new TrackedTransientAllocation(buffer, lease);
    }

    public static TrackedTransientAllocation calloc(String ownerId, int byteCount) {
        int size = Math.max(1, byteCount);
        ByteBuffer buffer = MemoryUtil.memCalloc(size);
        MemoryLease lease = UnifiedMemoryFabric.get()
                .openLease(MemoryDomain.FRAME_TRANSIENT, ownerId != null ? ownerId : "frame-transient")
                .update(size, size);
        return new TrackedTransientAllocation(buffer, lease);
    }

    public ByteBuffer buffer() {
        return buffer;
    }

    public long address() {
        return MemoryUtil.memAddress(buffer);
    }

    @Override
    public void close() {
        lease.close();
        MemoryUtil.memFree(buffer);
    }
}
