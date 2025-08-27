package rogo.sketch.render.vertex;

import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.api.BufferResourceObject;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.filler.VertexSorting;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.data.sorting.PrimitiveSorter;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced index buffer with support for different index types and sorting
 * Now maintains a persistent ByteBuffer to avoid frequent allocations
 */
public class IndexBufferResource implements BufferResourceObject {
    private int id;
    private final List<Integer> indices;
    private boolean disposed = false;
    private boolean isDirty;

    private ByteBuffer persistentBuffer;
    private IndexType currentIndexType = IndexType.UINT;
    private int bufferCapacity = 0;

    public IndexBufferResource() {
        this.indices = new ArrayList<>();
        this.isDirty = false;
        this.id = GL15.glGenBuffers();
    }

    /**
     * Add a single index
     */
    public void addIndex(int index) {
        indices.add(index);
        isDirty = true;
    }

    /**
     * Get the current number of indices
     */
    public int getIndexCount() {
        return indices.size();
    }

    /**
     * Clear all indices
     */
    public void clear() {
        indices.clear();
        isDirty = true;
    }

    /**
     * Ensure the persistent buffer has enough capacity
     */
    private void ensureBufferCapacity(int requiredIndices, IndexType indexType) {
        int requiredBytes = requiredIndices * indexType.bytes;

        if (persistentBuffer == null || bufferCapacity < requiredBytes || currentIndexType != indexType) {

            if (persistentBuffer != null) {
                MemoryUtil.memFree(persistentBuffer);
            }

            int newCapacity = Math.max(requiredBytes, requiredBytes * 2);
            persistentBuffer = MemoryUtil.memAlloc(newCapacity);
            bufferCapacity = newCapacity;
            currentIndexType = indexType;
        }

        persistentBuffer.clear();
    }

    /**
     * Apply sorting to indices for transparent rendering
     * This is the main entry point for transparency sorting
     *
     * @param vertexData    Combined vertex data buffer
     * @param format        Vertex data format
     * @param primitiveType Type of primitives
     * @param vertexCount   Total number of vertices
     * @param sorting       Sorting algorithm to use
     */
    public void applySorting(ByteBuffer vertexData, DataFormat format,
                             PrimitiveType primitiveType, int vertexCount,
                             VertexSorting sorting) {
        if (!PrimitiveSorter.canSort(primitiveType) || sorting == null) {
            return; // Cannot sort this primitive type
        }

        // Calculate sorted primitive order
        int[] sortedOrder = PrimitiveSorter.calculateSortedOrder(
                vertexData, format, primitiveType, vertexCount, sorting
        );

        // Apply the sorting to indices
        applySortingByOrder(sortedOrder, primitiveType);
    }

    /**
     * Apply sorting based on pre-calculated primitive order
     *
     * @deprecated Use applySorting(ByteBuffer, DataFormat, PrimitiveType, int, VertexSorting) instead
     */
    @Deprecated
    public void applySorting(int[] sortedOrder) {
        // Determine primitive type based on index count (fallback)
        int primitiveSize = determinePrimitiveSize();
        if (primitiveSize == 0) {
            return;
        }

        applySortingByOrder(sortedOrder, null);
    }

    /**
     * Internal method to reorder indices based on sorted primitive order
     */
    private void applySortingByOrder(int[] sortedOrder, PrimitiveType primitiveType) {
        if (sortedOrder.length == 0) {
            return;
        }

        int indicesPerPrimitive;
        if (primitiveType != null) {
            // Use accurate calculation based on primitive type
            indicesPerPrimitive = calculateIndicesPerPrimitive(primitiveType);
        } else {
            // Fallback to heuristic method
            indicesPerPrimitive = determinePrimitiveSize();
        }

        if (indicesPerPrimitive == 0) {
            return;
        }

        List<Integer> sortedIndices = new ArrayList<>();

        for (int primitiveIndex : sortedOrder) {
            int baseIndex = primitiveIndex * indicesPerPrimitive;
            for (int i = 0; i < indicesPerPrimitive; i++) {
                if (baseIndex + i < indices.size()) {
                    sortedIndices.add(indices.get(baseIndex + i));
                }
            }
        }

        indices.clear();
        indices.addAll(sortedIndices);
        isDirty = true;
    }

    /**
     * Calculate indices per primitive based on primitive type
     */
    private int calculateIndicesPerPrimitive(PrimitiveType primitiveType) {
        switch (primitiveType) {
            case QUADS:
                return 6; // 2 triangles
            case LINES:
                return 6; // Expanded to quad (2 triangles)
            case TRIANGLES:
                return 3; // 1 triangle
            case POINTS:
                return 1; // 1 point
            default:
                return primitiveType.getVerticesPerPrimitive();
        }
    }

    private int determinePrimitiveSize() {
        // Try to determine if we're dealing with triangles (3) or quads as triangles (6)
        int count = indices.size();
        if (count % 6 == 0) {
            return 6; // Quads as triangles
        } else if (count % 3 == 0) {
            return 3; // Triangles
        } else if (count % 2 == 0) {
            return 2; // Lines
        } else {
            return 1; // Points
        }
    }

    /**
     * Fill the persistent buffer with indices in the specified format
     * Returns the buffer ready for upload (flipped)
     */
    private ByteBuffer fillPersistentBuffer(IndexType indexType) {
        int indexCount = indices.size();
        ensureBufferCapacity(indexCount, indexType);

        switch (indexType) {
            case UBYTE -> {
                for (int index : indices) {
                    persistentBuffer.put((byte) (index & 0xFF));
                }
            }
            case USHORT -> {
                ShortBuffer shortBuffer = persistentBuffer.asShortBuffer();
                for (int index : indices) {
                    shortBuffer.put((short) (index & 0xFFFF));
                }
                persistentBuffer.position(persistentBuffer.position() + indexCount * 2);
            }
            case UINT -> {
                IntBuffer intBuffer = persistentBuffer.asIntBuffer();
                for (int index : indices) {
                    intBuffer.put(index);
                }
                persistentBuffer.position(persistentBuffer.position() + indexCount * 4);
            }
        }

        persistentBuffer.flip();
        return persistentBuffer;
    }

    /**
     * Upload indices to GPU buffer using persistent buffer
     */
    public void upload(IndexType indexType) {
        if (!isDirty && id > 0) {
            return; // Already uploaded and not dirty
        }

        if (indices.isEmpty()) {
            return; // Nothing to upload
        }

        ByteBuffer buffer = fillPersistentBuffer(indexType);
        bind();
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        //unbind();

        isDirty = false;
    }

    /**
     * Upload indices to GPU buffer with automatic index type selection
     */
    public void upload() {
        upload(determineOptimalIndexType());
    }

    /**
     * Determine the optimal index type based on the maximum index value
     */
    public IndexType determineOptimalIndexType() {
        if (indices.isEmpty()) {
            return IndexType.UINT;
        }

        int maxIndex = indices.stream().mapToInt(Integer::intValue).max().orElse(0);

        if (maxIndex <= 255) {
            return IndexType.UBYTE;
        } else if (maxIndex <= 65535) {
            return IndexType.USHORT;
        } else {
            return IndexType.UINT;
        }
    }

    /**
     * Get a copy of all indices
     */
    public int[] getIndices() {
        return indices.stream().mapToInt(Integer::intValue).toArray();
    }

    public IndexType currentIndexType() {
        return currentIndexType;
    }

    /**
     * Check if the buffer needs to be reuploaded
     */
    public boolean isDirty() {
        return isDirty;
    }

    @Override
    public int getHandle() {
        return id;
    }

    @Override
    public void bind() {
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, id);
    }

    @Override
    public void unbind() {
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    @Override
    public void dispose() {
        if (id > 0) {
            GL15.glDeleteBuffers(id);
            id = 0;
        }

        // Free persistent buffer
        if (persistentBuffer != null) {
            MemoryUtil.memFree(persistentBuffer);
            persistentBuffer = null;
        }

        indices.clear();
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

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
}