package rogo.sketch.render.data.filler;

import org.lwjgl.system.MemoryUtil;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.format.DataFormat;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Enhanced vertex data filler with automatic vertex counting, index buffer support,
 * and vertex sorting capabilities. Based on Minecraft's BufferBuilder design.
 */
public class VertexFiller extends DataFiller {
    private final List<ByteBuffer> vertexData;
    private final int vertexStride;
    private int vertexCount;
    private final PrimitiveType primitiveType;
    private VertexSorting sorting = VertexSorting.DISTANCE_TO_ORIGIN;
    private boolean shouldSorting = true;
    private ByteBuffer currentBuffer;
    private final int initialBufferSize;
    private final int bufferGrowthSize;
    
    // Matrix stack for transformations
    private final Stack<float[]> matrixStack = new Stack<>();
    private int vertexOffset = 0;

    public VertexFiller(DataFormat format, PrimitiveType primitiveType) {
        this(format, primitiveType, 1024);
    }

    public VertexFiller(DataFormat format, PrimitiveType primitiveType, int initialVertexCapacity) {
        super(format);
        this.primitiveType = primitiveType;
        this.vertexStride = format.getStride();
        this.initialBufferSize = initialVertexCapacity * vertexStride;
        this.bufferGrowthSize = 2097152; // 2MB growth, like MC
        this.vertexData = new ArrayList<>();
        this.vertexCount = 0;

        allocateNewBuffer();
    }

    private void allocateNewBuffer() {
        currentBuffer = MemoryUtil.memAlloc(initialBufferSize);
        currentBuffer.clear();
    }

    private void ensureCapacity(int additionalBytes) {
        if (currentBuffer.remaining() < additionalBytes) {
            // Grow buffer
            int newCapacity = Math.max(
                    currentBuffer.capacity() * 2,
                    currentBuffer.capacity() + additionalBytes + bufferGrowthSize
            );

            ByteBuffer newBuffer = MemoryUtil.memAlloc(newCapacity);

            // Copy existing data
            currentBuffer.flip();
            newBuffer.put(currentBuffer);

            MemoryUtil.memFree(currentBuffer);
            currentBuffer = newBuffer;
        }
    }

    /**
     * Finalize the current buffer and add it to vertex data
     */
    private void finalizeCurrentBuffer() {
        if (currentBuffer.position() > 0) {
            currentBuffer.flip();

            // Create a copy for storage
            ByteBuffer finalBuffer = MemoryUtil.memAlloc(currentBuffer.remaining());
            finalBuffer.put(currentBuffer);
            finalBuffer.flip();

            vertexData.add(finalBuffer);

            // Reset current buffer for potential reuse
            currentBuffer.clear();
        }
    }

    /**
     * Set the sorting method
     */
    public VertexFiller setSorting(VertexSorting sorting) {
        this.sorting = sorting;
        enableSorting();
        return this;
    }

    public void enableSorting() {
        if (primitiveType.supportsSorting()) {
            this.shouldSorting = true;
        } else {
            throw new IllegalStateException("primitiveType dose not support sorting");
        }
    }

    public void disableSorting() {
        this.shouldSorting = false;
    }

    /**
     * Set sorting by distance from a point
     */
    public VertexFiller sortByDistance(float x, float y, float z) {
        return setSorting(VertexSorting.byDistance(x, y, z));
    }

    /**
     * Set sorting by distance from origin
     */
    public VertexFiller sortByDistanceToOrigin() {
        return setSorting(VertexSorting.DISTANCE_TO_ORIGIN);
    }

    /**
     * Set orthographic Z sorting
     */
    public VertexFiller sortOrthographicZ() {
        return setSorting(VertexSorting.ORTHOGRAPHIC_Z);
    }

    @Override
    public VertexFiller nextVertex() {
        // Complete current vertex
        completeCurrentVertex();

        // Move to next vertex
        vertexCount++;
        currentVertex++;
        currentElementIndex = 0;

        // Ensure capacity for next vertex
        ensureCapacity(vertexStride);

        return this;
    }

    private void completeCurrentVertex() {
        // Ensure we're at the end of the current vertex
        while (currentElementIndex < format.getElementCount()) {
            // Pad with zeros if vertex is incomplete
            writeFloat(0.0f);
            currentElementIndex++;
        }
    }

    @Override
    public void writeFloat(float value) {
        ensureCapacity(4);
        currentBuffer.putFloat(value);
    }

    @Override
    public void writeInt(int value) {
        ensureCapacity(4);
        currentBuffer.putInt(value);
    }

    @Override
    public void writeUInt(int value) {
        writeInt(value); // Same as int in OpenGL
    }

    @Override
    public void writeByte(byte value) {
        ensureCapacity(1);
        currentBuffer.put(value);
    }

    @Override
    public void writeUByte(byte value) {
        writeByte(value); // Same as byte
    }

    @Override
    public void writeShort(short value) {
        ensureCapacity(2);
        currentBuffer.putShort(value);
    }

    @Override
    public void writeUShort(short value) {
        writeShort(value); // Same as short
    }

    @Override
    public void writeDouble(double value) {
        ensureCapacity(8);
        currentBuffer.putDouble(value);
    }


    /**
     * Get current vertex count
     */
    public int getVertexCount() {
        return vertexCount;
    }

    /**
     * Get current index count (calculated based on vertex count and primitive type)
     */
    public int getIndexCount() {
        return primitiveType.requiresIndexBuffer() ? primitiveType.calculateIndexCount(vertexCount) : 0;
    }

    /**
     * Check if index buffer is enabled
     */
    public boolean isUsingIndexBuffer() {
        return primitiveType.requiresIndexBuffer();
    }

    /**
     * Check if sorting is enabled
     */
    public boolean isSortingEnabled() {
        return this.shouldSorting;
    }

    @Override
    public void end() {
        validateVertexCompletion();
        validatePrimitiveRequirements();

        if (currentBuffer.position() > 0) {
            finalizeCurrentBuffer();
        }
    }

    /**
     * Get the sorting method (if any)
     */
    @Nullable
    public VertexSorting getSorting() {
        return sorting;
    }

    /**
     * Override to provide VertexFiller-specific vertex completion logic
     */
    @Override
    public boolean isCurrentVertexComplete() {
        // If we have at least one complete vertex and currentElementIndex is 0,
        // it means advanceElement() auto-called nextVertex() after completing the last vertex
        if (vertexCount > 0 && currentElementIndex == 0) {
            return true;
        }

        // Otherwise, use the standard check
        return currentElementIndex >= format.getElementCount();
    }

    /**
     * Validate that the current vertex is complete
     */
    private void validateVertexCompletion() {
        if (!isCurrentVertexComplete()) {
            throw new IllegalStateException("Current vertex is incomplete. " +
                    "Missing " + getRemainingElementsInVertex() + " elements. " +
                    "Make sure to fill all required vertex attributes before calling end().");
        }
    }

    /**
     * Validate that we have the correct number of vertices for the primitive type
     */
    private void validatePrimitiveRequirements() {
        if (!primitiveType.isValidVertexCount(vertexCount)) {
            throw new IllegalStateException("Invalid vertex count (" + vertexCount +
                    ") for primitive type " + primitiveType +
                    ". Expected multiple of " + primitiveType.getVerticesPerPrimitive() + " vertices.");
        }
    }


    /**
     * Get the primitive type for this filler
     */
    public PrimitiveType getPrimitiveType() {
        return primitiveType;
    }

    /**
     * Clear all vertex data for reuse
     */
    public void clear() {
        // Clear vertex data
        for (ByteBuffer buffer : vertexData) {
            MemoryUtil.memFree(buffer);
        }
        vertexData.clear();

        // Reset counters
        vertexCount = 0;
        currentVertex = 0;
        currentElementIndex = 0;
        vertexOffset = 0;

        // Reset current buffer
        if (currentBuffer != null) {
            currentBuffer.clear();
        }

        // Clear sorting data
        sorting = null;
        
        // Clear matrix stack
        matrixStack.clear();
    }
    
    /**
     * Reset the filler to reuse for new data
     */
    public void reset() {
        clear();
        allocateNewBuffer();
    }
    
    /**
     * Set the vertex offset for this filler
     */
    public void setVertexOffset(int offset) {
        this.vertexOffset = offset;
        this.currentVertex = offset;
    }
    
    /**
     * Get the current vertex offset
     */
    public int getVertexOffset() {
        return vertexOffset;
    }
    
    /**
     * Push a transformation matrix onto the stack
     */
    public void pushMatrix(float[] matrix) {
        if (matrix.length != 16) {
            throw new IllegalArgumentException("Matrix must be 4x4 (16 elements)");
        }
        float[] copy = new float[16];
        System.arraycopy(matrix, 0, copy, 0, 16);
        matrixStack.push(copy);
    }
    
    /**
     * Pop a transformation matrix from the stack
     */
    public void popMatrix() {
        if (matrixStack.isEmpty()) {
            throw new IllegalStateException("Matrix stack is empty");
        }
        matrixStack.pop();
    }
    
    /**
     * Get the current transformation matrix (top of stack)
     */
    @Nullable
    public float[] getCurrentMatrix() {
        return matrixStack.isEmpty() ? null : matrixStack.peek();
    }
    
    /**
     * Check if matrix stack is empty
     */
    public boolean hasMatrix() {
        return !matrixStack.isEmpty();
    }

    /**
     * Get all vertex data as a single ByteBuffer for upload to GPU
     */
    public ByteBuffer getVertexData() {
        if (vertexData.isEmpty()) {
            return null;
        }

        // Calculate total size
        int totalSize = 0;
        for (ByteBuffer buffer : vertexData) {
            totalSize += buffer.remaining();
        }

        // Add current buffer if it has data
        if (currentBuffer != null && currentBuffer.position() > 0) {
            totalSize += currentBuffer.position();
        }

        // Create combined buffer
        ByteBuffer combined = MemoryUtil.memAlloc(totalSize);

        // Copy all data
        for (ByteBuffer buffer : vertexData) {
            combined.put(buffer);
        }

        // Add current buffer data if any
        if (currentBuffer != null && currentBuffer.position() > 0) {
            currentBuffer.flip();
            combined.put(currentBuffer);
            currentBuffer.clear();
        }

        combined.flip();
        return combined;
    }

    /**
     * Release native memory
     */
    public void dispose() {
        if (currentBuffer != null) {
            MemoryUtil.memFree(currentBuffer);
            currentBuffer = null;
        }

        // Free any remaining buffers
        for (ByteBuffer buffer : vertexData) {
            MemoryUtil.memFree(buffer);
        }
        vertexData.clear();
    }
}