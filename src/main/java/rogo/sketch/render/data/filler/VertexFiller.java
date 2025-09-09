package rogo.sketch.render.data.filler;

import org.lwjgl.system.MemoryUtil;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.data.format.DataElement;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced vertex data filler with automatic vertex counting, semantic methods,
 * and vertex sorting capabilities. Delegates actual data writing to an internal DataFiller.
 */
public class VertexFiller extends DataFiller {
    private DataFiller internalFiller;
    private final PrimitiveType primitiveType;
    private final Map<String, Integer> elementOffsetMap;
    
    // Vertex management
    private long currentVertexIndex = 0;
    private int currentElementIndex = 0;
    private int vertexCount = 0;
    
    // Sorting
    private VertexSorting sorting = VertexSorting.DISTANCE_TO_ORIGIN;
    private boolean shouldSort = true;
    
    // Buffer management
    private final List<ByteBuffer> vertexData;
    private ByteBuffer currentBuffer;
    private final int initialBufferSize;
    private final int bufferGrowthSize;

    public VertexFiller(DataFormat format, PrimitiveType primitiveType) {
        this(format, primitiveType, 1024);
    }

    public VertexFiller(DataFormat format, PrimitiveType primitiveType, int initialVertexCapacity) {
        super(format);
        this.primitiveType = primitiveType;
        this.initialBufferSize = initialVertexCapacity * format.getStride();
        this.bufferGrowthSize = 2097152; // 2MB growth
        this.vertexData = new ArrayList<>();
        this.elementOffsetMap = buildElementOffsetMap(format);

        allocateNewBuffer();
        this.internalFiller = ByteBufferFiller.wrap(format, currentBuffer);
    }

    private Map<String, Integer> buildElementOffsetMap(DataFormat format) {
        Map<String, Integer> map = new HashMap<>();
        for (DataElement element : format.getElements()) {
            map.put(element.getName(), element.getOffset());
        }
        return map;
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
            
            // Update internal filler
            this.internalFiller = ByteBufferFiller.wrap(format, currentBuffer);
        }
    }

    // ===== Core data writing methods delegated to internal filler =====

    @Override
    public DataFiller putFloat(float value) {
        ensureCapacity(Float.BYTES);
        internalFiller.putFloat(value);
        advanceElement();
        return this;
    }

    @Override
    public DataFiller putInt(int value) {
        ensureCapacity(Integer.BYTES);
        internalFiller.putInt(value);
        advanceElement();
        return this;
    }

    @Override
    public DataFiller putUInt(int value) {
        ensureCapacity(Integer.BYTES);
        internalFiller.putUInt(value);
        advanceElement();
        return this;
    }

    @Override
    public DataFiller putByte(byte value) {
        ensureCapacity(Byte.BYTES);
        internalFiller.putByte(value);
        advanceElement();
        return this;
    }

    @Override
    public DataFiller putUByte(byte value) {
        ensureCapacity(Byte.BYTES);
        internalFiller.putUByte(value);
        advanceElement();
        return this;
    }

    @Override
    public DataFiller putShort(short value) {
        ensureCapacity(Short.BYTES);
        internalFiller.putShort(value);
        advanceElement();
        return this;
    }

    @Override
    public DataFiller putUShort(short value) {
        ensureCapacity(Short.BYTES);
        internalFiller.putUShort(value);
        advanceElement();
        return this;
    }

    @Override
    public DataFiller putDouble(double value) {
        ensureCapacity(Double.BYTES);
        internalFiller.putDouble(value);
        advanceElement();
        return this;
    }

    // ===== Vertex semantic convenience methods =====

    /**
     * Write position (assumes first 3 floats are position)
     */
    public VertexFiller position(float x, float y, float z) {
        putVec3(x, y, z);
        return this;
    }

    /**
     * Write normal vector
     */
    public VertexFiller normal(float x, float y, float z) {
        putVec3(x, y, z);
        return this;
    }

    /**
     * Write texture coordinates
     */
    public VertexFiller uv(float u, float v) {
        putVec2(u, v);
        return this;
    }

    /**
     * Write color (RGBA)
     */
    public VertexFiller color(float r, float g, float b, float a) {
        putVec4(r, g, b, a);
        return this;
    }

    /**
     * Write color as bytes (0-255 range)
     */
    public VertexFiller colorByte(int r, int g, int b, int a) {
        putVec4ub(r, g, b, a);
        return this;
    }

    /**
     * Write tangent vector
     */
    public VertexFiller tangent(float x, float y, float z, float w) {
        putVec4(x, y, z, w);
        return this;
    }

    /**
     * Write bone weights
     */
    public VertexFiller boneWeights(float w1, float w2, float w3, float w4) {
        putVec4(w1, w2, w3, w4);
        return this;
    }

    /**
     * Write bone indices
     */
    public VertexFiller boneIndices(int i1, int i2, int i3, int i4) {
        putIVec4(i1, i2, i3, i4);
        return this;
    }

    // ===== Vertex management =====

    /**
     * Advance to the next vertex
     */
    public VertexFiller nextVertex() {
        completeCurrentVertex();
        vertexCount++;
        currentVertexIndex++;
        currentElementIndex = 0;
        ensureCapacity(format.getStride());
        return this;
    }

    /**
     * Move to a specific vertex index
     */
    public VertexFiller vertex(long index) {
        this.currentVertexIndex = index;
        this.currentElementIndex = 0;
        // Position internal filler to vertex start
        long byteOffset = index * format.getStride();
        if (internalFiller.supportsRandomAccess()) {
            ((DirectDataFiller) internalFiller).setPosition(byteOffset);
        }
        return this;
    }

    private void completeCurrentVertex() {
        // Pad incomplete vertices with zeros
        while (currentElementIndex < format.getElementCount()) {
            putFloat(0.0f);
            currentElementIndex++;
        }
    }

    private void advanceElement() {
        currentElementIndex++;
        if (currentElementIndex >= format.getElementCount()) {
            nextVertex();
        }
    }

    // ===== Random access methods for indexed filling =====

    /**
     * Write position at specific vertex index
     */
    public VertexFiller positionAt(long vertexIndex, float x, float y, float z) {
        long baseOffset = vertexIndex * format.getStride();
        Integer elementOffset = elementOffsetMap.get("position");
        if (elementOffset == null) {
            elementOffset = 0; // Assume position is first element
        }
        
        long offset = baseOffset + elementOffset;
        putFloatAt(offset, x);
        putFloatAt(offset + Float.BYTES, y);
        putFloatAt(offset + 2 * Float.BYTES, z);
        return this;
    }

    /**
     * Write color at specific vertex index
     */
    public VertexFiller colorAt(long vertexIndex, float r, float g, float b, float a) {
        long baseOffset = vertexIndex * format.getStride();
        Integer elementOffset = elementOffsetMap.get("color");
        if (elementOffset == null) {
            throw new IllegalArgumentException("Color element not found in format");
        }
        
        long offset = baseOffset + elementOffset;
        putFloatAt(offset, r);
        putFloatAt(offset + Float.BYTES, g);
        putFloatAt(offset + 2 * Float.BYTES, b);
        putFloatAt(offset + 3 * Float.BYTES, a);
        return this;
    }

    @Override
    public void putFloatAt(long byteOffset, float value) {
        if (internalFiller.supportsRandomAccess()) {
            internalFiller.putFloatAt(byteOffset, value);
        } else {
            throw new UnsupportedOperationException("Random access not supported");
        }
    }

    // ===== Sorting methods =====

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
            this.shouldSort = true;
        } else {
            throw new IllegalStateException("Primitive type does not support sorting");
        }
    }

    public void disableSorting() {
        this.shouldSort = false;
    }

    /**
     * Set sorting by distance from a point
     */
    public VertexFiller sortByDistance(float x, float y, float z) {
        return setSorting(VertexSorting.byDistance(x, y, z));
    }

    // ===== Getters =====

    public int getVertexCount() {
        return vertexCount;
    }

    public int getIndexCount() {
        return primitiveType.requiresIndexBuffer() ? primitiveType.calculateIndexCount(vertexCount) : 0;
    }

    public boolean isUsingIndexBuffer() {
        return primitiveType.requiresIndexBuffer();
    }

    public boolean isSortingEnabled() {
        return this.shouldSort;
    }

    public PrimitiveType getPrimitiveType() {
        return primitiveType;
    }

    @Nullable
    public VertexSorting getSorting() {
        return sorting;
    }

    // ===== Buffer management =====

    /**
     * Get all vertex data as a single ByteBuffer for upload to GPU
     */
    public ByteBuffer getVertexData() {
        if (vertexData.isEmpty() && currentBuffer.position() == 0) {
            return null;
        }

        // Calculate total size
        int totalSize = 0;
        for (ByteBuffer buffer : vertexData) {
            totalSize += buffer.remaining();
        }

        // Add current buffer if it has data
        if (currentBuffer.position() > 0) {
            totalSize += currentBuffer.position();
        }

        // Create combined buffer
        ByteBuffer combined = MemoryUtil.memAlloc(totalSize);

        // Copy all data
        for (ByteBuffer buffer : vertexData) {
            combined.put(buffer);
        }

        // Add current buffer data if any
        if (currentBuffer.position() > 0) {
            currentBuffer.flip();
            combined.put(currentBuffer);
            currentBuffer.clear();
        }

        combined.flip();
        return combined;
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
        currentVertexIndex = 0;
        currentElementIndex = 0;

        // Reset current buffer
        if (currentBuffer != null) {
            currentBuffer.clear();
        }
    }

    /**
     * Reset the filler to reuse for new data
     */
    public void reset() {
        clear();
        allocateNewBuffer();
    }

    @Override
    public void finish() {
        if (currentBuffer.position() > 0) {
            currentBuffer.flip();
            
            // Create a copy for storage
            ByteBuffer finalBuffer = MemoryUtil.memAlloc(currentBuffer.remaining());
            finalBuffer.put(currentBuffer);
            finalBuffer.flip();
            
            vertexData.add(finalBuffer);
            
            // Reset current buffer
            currentBuffer.clear();
        }
        
        validateVertexCompletion();
        validatePrimitiveRequirements();
    }

    private void validateVertexCompletion() {
        if (currentElementIndex != 0 && currentElementIndex < format.getElementCount()) {
            throw new IllegalStateException("Current vertex is incomplete. " +
                    "Missing " + (format.getElementCount() - currentElementIndex) + " elements.");
        }
    }

    private void validatePrimitiveRequirements() {
        if (!primitiveType.isValidVertexCount(vertexCount)) {
            throw new IllegalStateException("Invalid vertex count (" + vertexCount +
                    ") for primitive type " + primitiveType +
                    ". Expected multiple of " + primitiveType.getVerticesPerPrimitive() + " vertices.");
        }
    }


    /**
     * Release native memory
     */
    public void dispose() {
        if (currentBuffer != null) {
            MemoryUtil.memFree(currentBuffer);
            currentBuffer = null;
        }

        for (ByteBuffer buffer : vertexData) {
            MemoryUtil.memFree(buffer);
        }
        vertexData.clear();
    }
}