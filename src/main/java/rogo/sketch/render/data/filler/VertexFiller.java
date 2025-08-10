package rogo.sketch.render.data.filler;

import org.joml.Vector3f;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.vertex.IndexBufferResource;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced vertex data filler with automatic vertex counting, index buffer support,
 * and vertex sorting capabilities. Based on Minecraft's BufferBuilder design.
 */
public class VertexFiller extends DataFiller {
    
    // Vertex data management
    private final List<ByteBuffer> vertexData;
    private final int vertexStride;
    private int vertexCount;
    
    // Index buffer support
    private IndexBufferResource indexBuffer;
    private boolean useIndexBuffer;
    
    // Vertex sorting support
    @Nullable
    private Vector3f[] sortingPoints;
    @Nullable
    private VertexSorting sorting;
    private boolean sortingEnabled;
    
    // Buffer management
    private ByteBuffer currentBuffer;
    private final int initialBufferSize;
    private final int bufferGrowthSize;
    
    public VertexFiller(DataFormat format) {
        this(format, 1024); // Default initial capacity for 1024 vertices
    }
    
    public VertexFiller(DataFormat format, int initialVertexCapacity) {
        super(format);
        this.vertexStride = format.getStride();
        this.initialBufferSize = initialVertexCapacity * vertexStride;
        this.bufferGrowthSize = 2097152; // 2MB growth, like MC
        this.vertexData = new ArrayList<>();
        this.vertexCount = 0;
        this.useIndexBuffer = false;
        this.sortingEnabled = false;
        
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
     * Enable index buffer usage
     */
    public VertexFiller enableIndexBuffer() {
        if (indexBuffer == null) {
            indexBuffer = new IndexBufferResource();
        }
        useIndexBuffer = true;
        return this;
    }
    
    /**
     * Disable index buffer usage
     */
    public VertexFiller disableIndexBuffer() {
        useIndexBuffer = false;
        return this;
    }
    
    /**
     * Add an index to the index buffer
     */
    public VertexFiller index(int index) {
        if (useIndexBuffer && indexBuffer != null) {
            indexBuffer.addIndex(index);
        }
        return this;
    }
    
    /**
     * Add multiple indices
     */
    public VertexFiller indices(int... indices) {
        if (useIndexBuffer && indexBuffer != null) {
            for (int index : indices) {
                indexBuffer.addIndex(index);
            }
        }
        return this;
    }
    
    /**
     * Add a triangle (3 indices)
     */
    public VertexFiller triangle(int v0, int v1, int v2) {
        return indices(v0, v1, v2);
    }
    
    /**
     * Add a quad (6 indices for 2 triangles)
     */
    public VertexFiller quad(int v0, int v1, int v2, int v3) {
        return indices(v0, v1, v2, v2, v3, v0);
    }
    
    /**
     * Enable vertex sorting for transparency
     */
    public VertexFiller enableSorting() {
        this.sortingEnabled = true;
        if (sortingPoints == null) {
            // Will be computed when finishing
            sortingPoints = new Vector3f[0];
        }
        return this;
    }
    
    /**
     * Set the sorting method
     */
    public VertexFiller setSorting(VertexSorting sorting) {
        this.sorting = sorting;
        this.sortingEnabled = true;
        return this;
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
    public DataFiller nextVertex() {
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
     * Finish building and return the completed buffer data
     */
    public VertexFillerResult finish() {
        // Complete the last vertex if needed
        if (currentElementIndex > 0) {
            completeCurrentVertex();
            vertexCount++;
        }
        
        // Prepare vertex buffer
        currentBuffer.flip();
        ByteBuffer vertexBuffer = MemoryUtil.memAlloc(currentBuffer.remaining());
        vertexBuffer.put(currentBuffer);
        vertexBuffer.flip();
        
        // Prepare index buffer if used
        ByteBuffer indexBufferData = null;
        int indexCount = 0;
        IndexType indexType = IndexType.UINT;
        
        if (useIndexBuffer && indexBuffer != null) {
            if (sortingEnabled && sorting != null) {
                // Apply sorting
                applySorting();
            }
            
            indexCount = indexBuffer.getIndexCount();
            indexType = IndexType.getOptimalType(vertexCount);
            indexBufferData = indexBuffer.createBuffer(indexType);
        }
        
        return new VertexFillerResult(
            format,
            vertexBuffer,
            vertexCount,
            indexBufferData,
            indexCount,
            indexType,
            useIndexBuffer
        );
    }
    
    private void applySorting() {
        if (sorting == null || indexBuffer == null) {
            return;
        }
        
        // Extract sorting points from vertex data
        computeSortingPoints();
        
        if (sortingPoints != null && sortingPoints.length > 0) {
            // Get sorted indices
            int[] sortedOrder = sorting.sort(sortingPoints);
            
            // Rebuild index buffer with sorted order
            indexBuffer.applySorting(sortedOrder);
        }
    }
    
    private void computeSortingPoints() {
        if (vertexCount == 0) {
            return;
        }
        
        // Find position element in format
        int positionElementIndex = -1;
        int positionOffset = 0;
        
        for (int i = 0; i < format.getElementCount(); i++) {
            var element = format.getElements().get(i);
            if ("position".equals(element.getName()) || 
                "pos".equals(element.getName()) ||
                i == 0) { // Assume first element is position if no named position
                positionElementIndex = i;
                break;
            }
            positionOffset += element.getDataType().getStride();
        }
        
        if (positionElementIndex == -1) {
            return; // No position data found
        }
        
        // Extract positions for sorting
        sortingPoints = new Vector3f[vertexCount];
        currentBuffer.rewind();
        
        for (int i = 0; i < vertexCount; i++) {
            int vertexStart = i * vertexStride + positionOffset;
            currentBuffer.position(vertexStart);
            
            float x = currentBuffer.getFloat();
            float y = currentBuffer.getFloat();
            float z = currentBuffer.getFloat();
            
            sortingPoints[i] = new Vector3f(x, y, z);
        }
    }
    
    /**
     * Clear all data and reset for reuse
     */
    public void clear() {
        vertexCount = 0;
        currentVertex = 0;
        currentElementIndex = 0;
        currentBuffer.clear();
        
        if (indexBuffer != null) {
            indexBuffer.clear();
        }
        
        sortingPoints = null;
        sortingEnabled = false;
    }
    
    /**
     * Get current vertex count
     */
    public int getVertexCount() {
        return vertexCount;
    }
    
    /**
     * Get current index count
     */
    public int getIndexCount() {
        return useIndexBuffer && indexBuffer != null ? indexBuffer.getIndexCount() : 0;
    }
    
    /**
     * Check if index buffer is enabled
     */
    public boolean isUsingIndexBuffer() {
        return useIndexBuffer;
    }
    
    /**
     * Check if sorting is enabled
     */
    public boolean isSortingEnabled() {
        return sortingEnabled;
    }
    
    /**
     * Release native memory
     */
    public void dispose() {
        if (currentBuffer != null) {
            MemoryUtil.memFree(currentBuffer);
            currentBuffer = null;
        }
        
        if (indexBuffer != null) {
            indexBuffer.dispose();
            indexBuffer = null;
        }
        
        // Free any remaining buffers
        for (ByteBuffer buffer : vertexData) {
            MemoryUtil.memFree(buffer);
        }
        vertexData.clear();
    }
    
    /**
     * Index type enumeration
     */
    public enum IndexType {
        UBYTE(GL15.GL_UNSIGNED_BYTE, 1),
        USHORT(GL15.GL_UNSIGNED_SHORT, 2),
        UINT(GL15.GL_UNSIGNED_INT, 4);
        
        public final int glType;
        public final int bytes;
        
        IndexType(int glType, int bytes) {
            this.glType = glType;
            this.bytes = bytes;
        }
        
        public static IndexType getOptimalType(int maxIndex) {
            if (maxIndex < 256) {
                return UBYTE;
            } else if (maxIndex < 65536) {
                return USHORT;
            } else {
                return UINT;
            }
        }
    }

    /**
     * Result of vertex filling operation
     */
    public static class VertexFillerResult {
        private final DataFormat format;
        private final ByteBuffer vertexBuffer;
        private final int vertexCount;
        private final ByteBuffer indexBuffer;
        private final int indexCount;
        private final IndexType indexType;
        private final boolean hasIndices;

        public VertexFillerResult(DataFormat format, ByteBuffer vertexBuffer, int vertexCount,
                                ByteBuffer indexBuffer, int indexCount, IndexType indexType, boolean hasIndices) {
            this.format = format;
            this.vertexBuffer = vertexBuffer;
            this.vertexCount = vertexCount;
            this.indexBuffer = indexBuffer;
            this.indexCount = indexCount;
            this.indexType = indexType;
            this.hasIndices = hasIndices;
        }

        public DataFormat getFormat() { return format; }
        public ByteBuffer getVertexBuffer() { return vertexBuffer; }
        public int getVertexCount() { return vertexCount; }
        public ByteBuffer getIndexBuffer() { return indexBuffer; }
        public int getIndexCount() { return indexCount; }
        public IndexType getIndexType() { return indexType; }
        public boolean hasIndices() { return hasIndices; }

        public void dispose() {
            if (vertexBuffer != null) {
                MemoryUtil.memFree(vertexBuffer);
            }
            if (indexBuffer != null) {
                MemoryUtil.memFree(indexBuffer);
            }
        }
    }
} 