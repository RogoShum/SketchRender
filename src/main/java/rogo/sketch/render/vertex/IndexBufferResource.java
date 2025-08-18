package rogo.sketch.render.vertex;

import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.api.BufferResourceObject;
import rogo.sketch.render.data.filler.VertexFiller;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced index buffer with support for different index types and sorting
 */
public class IndexBufferResource implements BufferResourceObject {
    private int id;
    private final List<Integer> indices;
    private boolean disposed = false;
    private boolean isDirty;
    
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
     * Add multiple indices
     */
    public void addIndices(int... indices) {
        for (int index : indices) {
            addIndex(index);
        }
    }
    
    /**
     * Add indices for a triangle
     */
    public void addTriangle(int v0, int v1, int v2) {
        addIndices(v0, v1, v2);
    }
    
    /**
     * Add indices for a quad (as two triangles)
     */
    public void addQuad(int v0, int v1, int v2, int v3) {
        addIndices(v0, v1, v2, v2, v3, v0);
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
     * Apply sorting to the indices based on sorted primitive order
     */
    public void applySorting(int[] sortedOrder) {
        if (sortedOrder.length == 0) {
            return;
        }
        
        // Determine primitive type based on index count
        int primitiveSize = determinePrimitiveSize();
        if (primitiveSize == 0) {
            return;
        }
        
        List<Integer> sortedIndices = new ArrayList<>();
        
        for (int primitiveIndex : sortedOrder) {
            int baseIndex = primitiveIndex * primitiveSize;
            for (int i = 0; i < primitiveSize; i++) {
                if (baseIndex + i < indices.size()) {
                    sortedIndices.add(indices.get(baseIndex + i));
                }
            }
        }
        
        indices.clear();
        indices.addAll(sortedIndices);
        isDirty = true;
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
     * Create a ByteBuffer with the indices in the specified format
     */
    public ByteBuffer createBuffer(VertexFiller.IndexType indexType) {
        int indexCount = indices.size();
        ByteBuffer buffer = MemoryUtil.memAlloc(indexCount * indexType.bytes);
        
        switch (indexType) {
            case UBYTE -> {
                for (int index : indices) {
                    buffer.put((byte) (index & 0xFF));
                }
            }
            case USHORT -> {
                ShortBuffer shortBuffer = buffer.asShortBuffer();
                for (int index : indices) {
                    shortBuffer.put((short) (index & 0xFFFF));
                }
                buffer.position(buffer.position() + indexCount * 2);
            }
            case UINT -> {
                IntBuffer intBuffer = buffer.asIntBuffer();
                for (int index : indices) {
                    intBuffer.put(index);
                }
                buffer.position(buffer.position() + indexCount * 4);
            }
        }
        
        buffer.flip();
        return buffer;
    }
    
    /**
     * Upload indices to GPU buffer
     */
    public void upload(VertexFiller.IndexType indexType) {
        if (!isDirty && id > 0) {
            return; // Already uploaded and not dirty
        }
        
        ByteBuffer buffer = createBuffer(indexType);
        
        bind();
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        unbind();
        
        MemoryUtil.memFree(buffer);
        isDirty = false;
    }
    
    /**
     * Get a copy of all indices
     */
    public int[] getIndices() {
        return indices.stream().mapToInt(Integer::intValue).toArray();
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

        indices.clear();
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}