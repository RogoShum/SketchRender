package rogo.sketch.render.model;

import rogo.sketch.render.data.PrimitiveType;

/**
 * Represents an instance of a compiled sub-mesh with GPU buffer offsets.
 * This is used in ModelMesh to track where each sub-mesh's data is located
 * in the combined vertex and index buffers.
 */
public class SubMeshInstance {
    private final SubMesh originalSubMesh;
    
    // Buffer offsets for GPU rendering
    private final int vertexOffset;      // Offset in vertices
    private final int indexOffset;       // Offset in indices
    private final int vertexCount;       // Number of vertices
    private final int indexCount;        // Number of indices
    
    // Rendering properties
    private boolean visible;
    private int renderPriority;
    private String materialName;
    
    public SubMeshInstance(SubMesh originalSubMesh, int vertexOffset, int indexOffset) {
        this.originalSubMesh = originalSubMesh;
        this.vertexOffset = vertexOffset;
        this.indexOffset = indexOffset;
        this.vertexCount = originalSubMesh.getVertexCount();
        this.indexCount = originalSubMesh.getIndexCount();
        
        // Copy rendering properties
        this.visible = originalSubMesh.isVisible();
        this.renderPriority = originalSubMesh.getRenderPriority();
        this.materialName = originalSubMesh.getMaterialName();
    }
    
    /**
     * Check if this sub-mesh instance uses index buffer
     */
    public boolean usesIndexBuffer() {
        return originalSubMesh.usesIndexBuffer();
    }
    
    /**
     * Get the primitive type for rendering (from parent mesh)
     */
    public PrimitiveType getPrimitiveType(Mesh parentMesh) {
        return parentMesh.getPrimitiveType();
    }
    
    /**
     * Get the bound bone (if any)
     */
    public MeshBone getBone() {
        return originalSubMesh.getBone();
    }
    
    /**
     * Check if this sub-mesh is bound to a bone
     */
    public boolean isBound() {
        return originalSubMesh.isBound();
    }
    
    // Getters
    public SubMesh getOriginalSubMesh() {
        return originalSubMesh;
    }
    
    public String getName() {
        return originalSubMesh.getName();
    }
    
    public int getId() {
        return originalSubMesh.getId();
    }
    
    public int getVertexOffset() {
        return vertexOffset;
    }
    
    public int getIndexOffset() {
        return indexOffset;
    }
    
    public int getVertexCount() {
        return vertexCount;
    }
    
    public int getIndexCount() {
        return indexCount;
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
    
    public String getMaterialName() {
        return materialName;
    }
    
    public void setMaterialName(String materialName) {
        this.materialName = materialName;
    }
    
    @Override
    public String toString() {
        return "SubMeshInstance{" +
                "name='" + getName() + '\'' +
                ", vertexOffset=" + vertexOffset +
                ", indexOffset=" + indexOffset +
                ", vertexCount=" + vertexCount +
                ", indexCount=" + indexCount +
                ", bone=" + (getBone() != null ? getBone().getName() : "none") +
                ", visible=" + visible +
                '}';
    }
}
