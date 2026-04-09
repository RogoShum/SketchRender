package rogo.sketch.core.data.builder;

/**
 * Lightweight writable range view for concurrent filling.
 * <p>
 * The slice reuses the parent's native memory and does not own it.
 */
public final class ParallelWritableSlice implements AutoCloseable {
    private final NativeWriteBuffer writer;
    private final long startOffset;
    private final long capacity;

    private ParallelWritableSlice(NativeWriteBuffer writer, long startOffset, long capacity) {
        this.writer = writer;
        this.startOffset = startOffset;
        this.capacity = capacity;
    }

    public static ParallelWritableSlice of(NativeWriteBuffer parent, long startOffset, long capacity) {
        if (parent == null) {
            throw new IllegalArgumentException("parent must not be null");
        }
        if (startOffset < 0L || capacity < 0L || startOffset + capacity > parent.getCapacity()) {
            throw new IndexOutOfBoundsException(
                    "Slice [" + startOffset + ", " + (startOffset + capacity) + ") exceeds parent capacity " + parent.getCapacity());
        }
        return new ParallelWritableSlice(
                NativeWriteBuffer.getExternal(parent.getBaseAddress() + startOffset, capacity),
                startOffset,
                capacity);
    }

    public NativeWriteBuffer writer() {
        return writer;
    }

    public long startOffset() {
        return startOffset;
    }

    public long capacity() {
        return capacity;
    }

    @Override
    public void close() {
        // Slice views borrow memory from the parent buffer and do not own it.
    }
}

