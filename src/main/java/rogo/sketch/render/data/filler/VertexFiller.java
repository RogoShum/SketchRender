package rogo.sketch.render.data.filler;

import org.lwjgl.system.MemoryUtil;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.format.DataFormat;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced vertex data filler with automatic vertex counting, index buffer support,
 * and vertex sorting capabilities. Based on Minecraft's BufferBuilder design.
 */
public class VertexFiller extends DataFiller {
    private final List<ByteBuffer> vertexData;
    private int vertexCount;
    private final PrimitiveType primitiveType;
    private VertexSorting sorting = VertexSorting.DISTANCE_TO_ORIGIN;
    private boolean shouldSorting = true;
    private ByteBuffer currentBuffer;
    private final int initialBufferSize;
    private final int bufferGrowthSize;
    private int vertexOffset = 0;

    public VertexFiller(DataFormat format, PrimitiveType primitiveType) {
        this(format, primitiveType, 1024);
    }

    public VertexFiller(DataFormat format, PrimitiveType primitiveType, int initialVertexCapacity) {
        super(format);
        this.primitiveType = primitiveType;
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

    // Indexed writing methods for async operations
    @Override
    public void writeFloatAt(int vertexIndex, int elementIndex, float value) {
        int bytePosition = calculateBytePosition(vertexIndex, elementIndex);
        ensureCapacity(bytePosition + 4);
        currentBuffer.putFloat(bytePosition, value);
    }

    @Override
    public void writeIntAt(int vertexIndex, int elementIndex, int value) {
        int bytePosition = calculateBytePosition(vertexIndex, elementIndex);
        ensureCapacity(bytePosition + 4);
        currentBuffer.putInt(bytePosition, value);
    }

    @Override
    public void writeUIntAt(int vertexIndex, int elementIndex, int value) {
        writeIntAt(vertexIndex, elementIndex, value); // Same as int in OpenGL
    }

    @Override
    public void writeByteAt(int vertexIndex, int elementIndex, byte value) {
        int bytePosition = calculateBytePosition(vertexIndex, elementIndex);
        ensureCapacity(bytePosition + 1);
        currentBuffer.put(bytePosition, value);
    }

    @Override
    public void writeUByteAt(int vertexIndex, int elementIndex, byte value) {
        writeByteAt(vertexIndex, elementIndex, value); // Same as byte
    }

    @Override
    public void writeShortAt(int vertexIndex, int elementIndex, short value) {
        int bytePosition = calculateBytePosition(vertexIndex, elementIndex);
        ensureCapacity(bytePosition + 2);
        currentBuffer.putShort(bytePosition, value);
    }

    @Override
    public void writeUShortAt(int vertexIndex, int elementIndex, short value) {
        writeShortAt(vertexIndex, elementIndex, value); // Same as short
    }

    @Override
    public void writeDoubleAt(int vertexIndex, int elementIndex, double value) {
        int bytePosition = calculateBytePosition(vertexIndex, elementIndex);
        ensureCapacity(bytePosition + 8);
        currentBuffer.putDouble(bytePosition, value);
    }

    /**
     * Calculate byte position for a specific vertex and element
     */
    protected int calculateBytePosition(int vertexIndex, int elementIndex) {
        if (elementIndex >= format.getElementCount()) {
            throw new IndexOutOfBoundsException("Element index " + elementIndex + " out of bounds for format with " + format.getElementCount() + " elements");
        }

        int vertexByteOffset = vertexIndex * vertexStride;
        int elementByteOffset = format.getElements().get(elementIndex).getOffset();
        return vertexByteOffset + elementByteOffset;
    }

    /**
     * High-level indexed vertex data filling for async operations
     *
     * @param vertexIndex The vertex index to fill
     * @param fillAction  Action that fills vertex data using indexed methods
     */
    public void fillVertexAt(int vertexIndex, Runnable fillAction) {
        if (!indexedMode) {
            throw new IllegalStateException("fillVertexAt requires indexed mode");
        }

        // Ensure buffer capacity for this vertex
        int requiredSize = (vertexIndex + 1) * vertexStride;
        ensureCapacity(requiredSize);

        // Execute fill action
        fillAction.run();

        // Update vertex count if needed
        if (vertexIndex >= vertexCount) {
            vertexCount = vertexIndex + 1;
        }
    }

    /**
     * Convenience method for position at vertex index
     */
    public VertexFiller positionAt(int vertexIndex, float x, float y, float z) {
        writeFloatAt(vertexIndex, 0, x);  // Assuming position is first element
        writeFloatAt(vertexIndex, 1, y);
        writeFloatAt(vertexIndex, 2, z);
        return this;
    }

    /**
     * Convenience method for color at vertex index
     */
    public VertexFiller colorAt(int vertexIndex, int elementOffset, float r, float g, float b, float a) {
        writeFloatAt(vertexIndex, elementOffset, r);
        writeFloatAt(vertexIndex, elementOffset + 1, g);
        writeFloatAt(vertexIndex, elementOffset + 2, b);
        writeFloatAt(vertexIndex, elementOffset + 3, a);
        return this;
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
    }

    /**
     * Reset the filler to reuse for new data
     */
    public void reset() {
        clear();
        allocateNewBuffer();
    }

    /**
     * Set the vertex offset for this filler (sequential mode only)
     */
    public void setVertexOffset(int offset) {
        if (indexedMode) {
            throw new IllegalStateException("setVertexOffset not supported in indexed mode");
        }
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