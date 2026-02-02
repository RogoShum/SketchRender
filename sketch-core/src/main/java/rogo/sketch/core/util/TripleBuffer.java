package rogo.sketch.core.util;

import java.util.function.Supplier;

public class TripleBuffer<T extends GraphicsData<T>> {
    private final T[] buffers;
    private int readIndex = 0;
    private int writeIndex = 1;
    private int prevIndex = 2;

    @SuppressWarnings("unchecked")
    public TripleBuffer(Supplier<T> factory) {
        buffers = (T[]) new GraphicsData[3];
        for (int i = 0; i < 3; i++) {
            buffers[i] = factory.get();
        }
    }

    public T getRead() {
        return buffers[readIndex];
    }

    public T getWrite() {
        return buffers[writeIndex];
    }

    public T getPrev() {
        return buffers[prevIndex];
    }

    public void swap() {
        // Rotate indices:
        // Current Read becomes Prev
        // Current Write becomes Read
        // Current Prev becomes Write (reused for next calculation)

        int tempPrev = prevIndex;
        prevIndex = readIndex;
        readIndex = writeIndex;
        writeIndex = tempPrev;

        // Before using the new Write buffer (old Prev), valid data might need to be
        // copied
        // if the calculation depends on the previous state.
        // But typically AsyncTick calculates new state based on inputs,
        // and usually we copy 'Read' (current valid) to 'Write' before modification
        // OR the user manually handles copyFrom if needed.
        // For zero-allocation, we just rotate.

        // Initialize the new write buffer with the current read state
        // This ensures the next calculation starts from the latest valid state
        buffers[writeIndex].copyFrom(buffers[readIndex]);
    }
}