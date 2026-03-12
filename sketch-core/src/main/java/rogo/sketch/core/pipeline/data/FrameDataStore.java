package rogo.sketch.core.pipeline.data;

import rogo.sketch.core.pipeline.kernel.annotation.AnyThread;
import rogo.sketch.core.pipeline.kernel.annotation.SyncOnly;

/**
 * Double-buffered wrapper around {@link PipelineDataStore}.
 * <p>
 * The async thread writes to the "write" buffer while the render thread
 * reads from the "read" buffer. A {@link #swap()} call on the main thread
 * atomically flips the two.
 * </p>
 * <p>
 * This replaces the single-buffer {@code pipelineDataStores} map in GraphicsPipeline
 * for data that needs to be produced asynchronously and consumed synchronously.
 * </p>
 */
public final class FrameDataStore {

    private PipelineDataStore readBuffer;
    private PipelineDataStore writeBuffer;

    public FrameDataStore(PipelineDataStore a, PipelineDataStore b) {
        this.readBuffer = a;
        this.writeBuffer = b;
    }

    /**
     * Get the buffer for reading (render thread).
     */
    @SyncOnly("Read buffer is consumed during GL rendering")
    public PipelineDataStore readBuffer() {
        return readBuffer;
    }

    /**
     * Get the buffer for writing (async thread).
     */
    @AnyThread("Write buffer is populated during async build")
    public PipelineDataStore writeBuffer() {
        return writeBuffer;
    }

    /**
     * Swap read and write buffers. Must be called on the main thread
     * between async completion and render execution.
     */
    @SyncOnly("Buffer swap must be atomic on main thread")
    public void swap() {
        PipelineDataStore tmp = readBuffer;
        readBuffer = writeBuffer;
        writeBuffer = tmp;
    }

    /**
     * Reset the write buffer for a new frame.
     */
    public void resetWriteBuffer() {
        writeBuffer.reset();
    }

    /**
     * Reset both buffers.
     */
    public void resetAll() {
        readBuffer.reset();
        writeBuffer.reset();
    }
}