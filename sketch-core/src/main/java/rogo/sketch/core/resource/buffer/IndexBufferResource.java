package rogo.sketch.core.resource.buffer;

import org.lwjgl.opengl.GL15;
import rogo.sketch.core.api.BufferResourceObject;
import rogo.sketch.core.data.IndexType;
import rogo.sketch.core.data.builder.UnsafeBatchBuilder;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.driver.internal.IGLBufferStrategy;

import java.util.Objects;

/**
 * Enhanced index buffer with support for different index types and sorting
 * Now maintains a persistent ByteBuffer to avoid frequent allocations
 * Uses GraphicsAPI buffer strategy for DSA/Legacy abstraction.
 */
public class IndexBufferResource implements BufferResourceObject {
    protected int id;
    protected int[] indices;
    protected boolean disposed = false;
    protected boolean dirty;
    protected boolean shared;
    protected IndexType currentIndexType = IndexType.U_INT;
    protected UnsafeBatchBuilder builder;

    /**
     * Get the buffer strategy from the current graphics API
     */
    private static IGLBufferStrategy getBufferStrategy() {
        return GraphicsDriver.getCurrentAPI().getBufferStrategy();
    }

    public IndexBufferResource(boolean shared) {
        this.indices = new int[0];
        this.dirty = false;
        this.id = getBufferStrategy().createBuffer();
        this.shared = shared;
    }

    public void setIndices(final int[] indices) {
        this.indices = Objects.requireNonNull(indices);
        this.dirty = true;
    }

    /**
     * Get the current number of indices
     */
    public int getIndexCount() {
        return indices.length;
    }

    protected void fillBuffer() {
        builder.reset();
        builder.put(indices);
    }

    /**
     * Upload indices to GPU buffer using persistent buffer
     */
    public void upload() {
        if (disposed) {
            throw new IllegalStateException("Buffer resource has been disposed");
        }

        if (!dirty && id > 0) {
            return; // Already uploaded and not dirty
        }

        if (indices.length == 0) {
            return; // Nothing to upload
        }

        if (builder == null) {
            builder = UnsafeBatchBuilder.createInternal(4L);
        }

        fillBuffer();

        IGLBufferStrategy strategy = getBufferStrategy();
        strategy.bufferData(id, builder.getWriteOffset(), builder.getBaseAddress(), GL15.GL_STATIC_DRAW);

        dirty = false;
    }

    /**
     * Get a copy of all indices
     */
    public int[] getIndices() {
        return indices;
    }

    public IndexType currentIndexType() {
        return currentIndexType;
    }

    /**
     * Check if the buffer needs to be reuploaded
     */
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public int getHandle() {
        return id;
    }

    public boolean isShared() {
        return shared;
    }

    @Override
    public void bind() {
        getBufferStrategy().bindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, id);
    }

    @Override
    public void unbind() {
        getBufferStrategy().bindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    @Override
    public void dispose() {
        if (id > 0) {
            getBufferStrategy().deleteBuffer(id);
            id = 0;
        }

        // Free persistent buffer
        if (builder != null) {
            builder.close();
        }

        indices = null;
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

}
