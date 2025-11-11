package rogo.sketch.render.model;

import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.data.vertex.Mesh;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a sub-mesh within a mesh.
 * Each sub-mesh is bound to a bone and contains vertex data.
 */
public class SubMesh {
    private final String name;
    private final int id;

    // Bone binding
    @Nullable
    private MeshBone bone;

    // Mesh data
    private final int vertexCount;
    private int indexCount; // Will be calculated based on mesh's primitive type
    private final DataFormat vertexFormat;

    // Material and rendering properties
    private String materialName;
    private boolean visible = true;
    private int renderPriority = 0;

    // Vertex data (stored as raw data, will be compiled to GPU buffers)
    private final List<Float> vertices;
    private final List<Integer> indices;

    public SubMesh(String name, int id, int vertexCount, DataFormat vertexFormat) {
        this.name = name;
        this.id = id;
        this.vertexCount = vertexCount;
        this.vertexFormat = vertexFormat;
        this.vertices = new ArrayList<>();
        this.indices = new ArrayList<>();
        this.indexCount = 0; // Will be set when attached to mesh
    }

    /**
     * Bind this sub-mesh to a bone
     */
    public void bindToBone(@Nullable MeshBone bone) {
        this.bone = bone;
    }

    /**
     * Add vertex data (raw float values based on vertex format)
     */
    public void addVertex(float... vertexData) {
        if (vertexData.length != getVertexFloatCount()) {
            throw new IllegalArgumentException("Invalid vertex data length. Expected " +
                    getVertexFloatCount() + " floats, got " + vertexData.length);
        }

        for (float value : vertexData) {
            vertices.add(value);
        }
    }

    /**
     * Add vertex data from a list
     */
    public void addVertices(List<Float> vertexData) {
        if (vertexData.size() % getVertexFloatCount() != 0) {
            throw new IllegalArgumentException("Vertex data size must be multiple of " + getVertexFloatCount());
        }
        vertices.addAll(vertexData);
    }

    /**
     * Add index data
     */
    public void addIndex(int index) {
        indices.add(index);
    }

    /**
     * Add multiple indices
     */
    public void addIndices(int... indexData) {
        for (int index : indexData) {
            indices.add(index);
        }
    }

    /**
     * Add indices from a list
     */
    public void addIndices(List<Integer> indexData) {
        indices.addAll(indexData);
    }

    /**
     * Get the number of float values per vertex based on the format
     */
    public int getVertexFloatCount() {
        return vertexFormat.getStride() / 4; // Assuming most data is float (4 bytes)
    }

    /**
     * Get vertex data as float array
     */
    public float[] getVertexData() {
        float[] data = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) {
            data[i] = vertices.get(i);
        }
        return data;
    }

    /**
     * Get index data as int array
     */
    public int[] getIndexData() {
        int[] data = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            data[i] = indices.get(i);
        }
        return data;
    }

    /**
     * Check if this sub-mesh is bound to a bone
     */
    public boolean isBound() {
        return bone != null;
    }

    /**
     * Check if this sub-mesh uses index buffer
     */
    public boolean usesIndexBuffer() {
        return !indices.isEmpty();
    }

    /**
     * Update index count based on primitive type (called by Mesh)
     */
    void updateIndexCount(PrimitiveType primitiveType) {
        if (primitiveType.requiresIndexBuffer()) {
            this.indexCount = primitiveType.calculateIndexCount(vertexCount);
        } else {
            this.indexCount = 0;
        }
    }

    /**
     * Get the actual vertex count from stored data
     */
    public int getActualVertexCount() {
        return vertices.size() / getVertexFloatCount();
    }

    /**
     * Get the actual index count from stored data
     */
    public int getActualIndexCount() {
        return indices.size();
    }

    /**
     * Validate that the sub-mesh data is consistent
     */
    public boolean isValid() {
        // Check vertex count consistency
        int actualVertexCount = getActualVertexCount();
        if (actualVertexCount != vertexCount) {
            return false;
        }

        // Check index count if using indices
        if (usesIndexBuffer()) {
            int actualIndexCount = getActualIndexCount();
            if (actualIndexCount != indexCount) {
                return false;
            }
        }

        return true; // Primitive type validation is now handled at Mesh level
    }

    /**
     * Clear all vertex and index data
     */
    public void clear() {
        vertices.clear();
        indices.clear();
    }

    // Getters
    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    @Nullable
    public MeshBone getBone() {
        return bone;
    }

    public int getVertexCount() {
        return vertexCount;
    }

    public int getIndexCount() {
        return indexCount;
    }


    public DataFormat getVertexFormat() {
        return vertexFormat;
    }

    public String getMaterialName() {
        return materialName;
    }

    public void setMaterialName(String materialName) {
        this.materialName = materialName;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public int getRenderPriority() {
        return renderPriority;
    }

    public void setRenderPriority(int renderPriority) {
        this.renderPriority = renderPriority;
    }

    @Override
    public String toString() {
        return "SubMesh{" +
                "name='" + name + '\'' +
                ", id=" + id +
                ", vertexCount=" + vertexCount +
                ", indexCount=" + indexCount +
                ", bone=" + (bone != null ? bone.getName() : "none") +
                ", materialName='" + materialName + '\'' +
                '}';
    }
}