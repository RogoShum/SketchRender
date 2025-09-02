package rogo.sketch.render.model;

import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.resource.buffer.VertexResource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a compiled mesh with data filled into GPU vertex buffers.
 * This is the GPU-ready version of a Mesh that can be used for efficient batch rendering.
 * 
 * ModelMesh stores vertex data in VertexResource and maintains offset information
 * for each sub-mesh to enable batch drawing commands.
 */
public class ModelMesh implements AutoCloseable {
    private final String name;
    private final Mesh originalMesh;
    
    // GPU resources
    private final VertexResource vertexResource;
    
    // Sub-mesh instances with buffer offsets
    private final List<SubMeshInstance> subMeshInstances;
    private final Map<String, SubMeshInstance> subMeshInstancesByName;
    
    // Bone data (copied from original mesh for animation)
    private final List<MeshBone> bones;
    private final Map<String, MeshBone> bonesByName;
    @Nullable
    private final MeshBone rootBone;
    
    // Metadata
    private final Map<String, Object> metadata;
    private boolean disposed = false;
    
    public ModelMesh(String name, Mesh originalMesh, VertexResource vertexResource, 
                     List<SubMeshInstance> subMeshInstances) {
        this.name = name;
        this.originalMesh = originalMesh;
        this.vertexResource = vertexResource;
        this.subMeshInstances = new ArrayList<>(subMeshInstances);
        this.subMeshInstancesByName = new HashMap<>();
        this.metadata = new HashMap<>();
        
        // Build sub-mesh lookup map
        for (SubMeshInstance instance : subMeshInstances) {
            subMeshInstancesByName.put(instance.getName(), instance);
        }
        
        // Copy bone hierarchy (for animation support)
        this.bones = new ArrayList<>(originalMesh.getAllBones());
        this.bonesByName = new HashMap<>();
        for (MeshBone bone : bones) {
            bonesByName.put(bone.getName(), bone);
        }
        this.rootBone = originalMesh.getRootBone();
        
        // Copy metadata
        this.metadata.putAll(originalMesh.getMetadata());
    }
    
    // === Rendering Methods ===
    
    /**
     * Bind the vertex resource for rendering
     */
    public void bind() {
        if (disposed) {
            throw new IllegalStateException("ModelMesh has been disposed");
        }
        vertexResource.bind();
    }
    
    /**
     * Unbind the vertex resource
     */
    public void unbind() {
        vertexResource.unbind();
    }
    
    /**
     * Get sub-mesh instances sorted by render priority
     */
    public List<SubMeshInstance> getSortedSubMeshes() {
        List<SubMeshInstance> sorted = new ArrayList<>(subMeshInstances);
        sorted.sort((a, b) -> Integer.compare(a.getRenderPriority(), b.getRenderPriority()));
        return sorted;
    }
    
    /**
     * Get visible sub-mesh instances
     */
    public List<SubMeshInstance> getVisibleSubMeshes() {
        List<SubMeshInstance> visible = new ArrayList<>();
        for (SubMeshInstance instance : subMeshInstances) {
            if (instance.isVisible()) {
                visible.add(instance);
            }
        }
        return visible;
    }
    
    /**
     * Get sub-mesh instances by primitive type (for batch rendering)
     */
    public Map<PrimitiveType, List<SubMeshInstance>> getSubMeshesByPrimitiveType() {
        Map<PrimitiveType, List<SubMeshInstance>> byType = new HashMap<>();
        PrimitiveType meshPrimitiveType = originalMesh.getPrimitiveType();
        
        // All sub-meshes in this model use the same primitive type
        byType.put(meshPrimitiveType, new ArrayList<>(subMeshInstances));
        
        return byType;
    }
    
    /**
     * Get sub-mesh instances bound to a specific bone
     */
    public List<SubMeshInstance> getSubMeshesForBone(MeshBone bone) {
        List<SubMeshInstance> result = new ArrayList<>();
        for (SubMeshInstance instance : subMeshInstances) {
            if (instance.getBone() == bone) {
                result.add(instance);
            }
        }
        return result;
    }
    
    /**
     * Get unbound sub-mesh instances
     */
    public List<SubMeshInstance> getUnboundSubMeshes() {
        List<SubMeshInstance> result = new ArrayList<>();
        for (SubMeshInstance instance : subMeshInstances) {
            if (!instance.isBound()) {
                result.add(instance);
            }
        }
        return result;
    }
    
    // === Sub-Mesh Access ===
    
    /**
     * Find a sub-mesh instance by name
     */
    @Nullable
    public SubMeshInstance findSubMesh(String name) {
        return subMeshInstancesByName.get(name);
    }
    
    /**
     * Find a sub-mesh instance by original sub-mesh
     */
    @Nullable
    public SubMeshInstance findSubMesh(SubMesh originalSubMesh) {
        for (SubMeshInstance instance : subMeshInstances) {
            if (instance.getOriginalSubMesh() == originalSubMesh) {
                return instance;
            }
        }
        return null;
    }
    
    // === Bone Access ===
    
    /**
     * Find a bone by name
     */
    @Nullable
    public MeshBone findBone(String name) {
        return bonesByName.get(name);
    }
    
    /**
     * Update bone transforms (for animation)
     */
    public void updateBoneTransforms() {
        // Bone transforms are updated in the bone objects themselves
        // This method can be used to trigger any necessary updates
        for (MeshBone bone : bones) {
            // Force update of global transforms
            bone.getGlobalTransform();
        }
    }
    
    // === Utility Methods ===
    
    /**
     * Check if the model mesh has animation support
     */
    public boolean hasAnimation() {
        return !bones.isEmpty();
    }
    
    /**
     * Check if the model mesh uses index buffers
     */
    public boolean usesIndexBuffer() {
        return vertexResource.hasIndices();
    }
    
    /**
     * Get total vertex count
     */
    public int getTotalVertexCount() {
        return vertexResource.getStaticVertexCount();
    }
    
    /**
     * Get total index count
     */
    public int getTotalIndexCount() {
        if (vertexResource.getIndexBuffer() != null) {
            return vertexResource.getIndexBuffer().getIndexCount();
        }
        return 0;
    }
    
    /**
     * Validate that the model mesh is consistent
     */
    public boolean isValid() {
        if (disposed) {
            return false;
        }
        
        // Check that vertex resource is valid
        if (vertexResource.isDisposed()) {
            return false;
        }
        
        // Check that all sub-mesh instances have valid offsets
        for (SubMeshInstance instance : subMeshInstances) {
            if (instance.getVertexOffset() < 0 || 
                instance.getVertexOffset() + instance.getVertexCount() > getTotalVertexCount()) {
                return false;
            }
            
            if (instance.usesIndexBuffer()) {
                if (instance.getIndexOffset() < 0 || 
                    instance.getIndexOffset() + instance.getIndexCount() > getTotalIndexCount()) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    // === Getters ===
    
    public String getName() {
        return name;
    }
    
    public Mesh getOriginalMesh() {
        return originalMesh;
    }
    
    public VertexResource getVertexResource() {
        return vertexResource;
    }
    
    public List<SubMeshInstance> getSubMeshInstances() {
        return new ArrayList<>(subMeshInstances);
    }
    
    public List<MeshBone> getBones() {
        return new ArrayList<>(bones);
    }
    
    @Nullable
    public MeshBone getRootBone() {
        return rootBone;
    }
    
    public int getSubMeshCount() {
        return subMeshInstances.size();
    }
    
    public int getBoneCount() {
        return bones.size();
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    @Nullable
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    public boolean isDisposed() {
        return disposed;
    }
    
    // === Resource Management ===
    
    @Override
    public void close() {
        dispose();
    }
    
    public void dispose() {
        if (!disposed) {
            vertexResource.dispose();
            disposed = true;
        }
    }
    
    @Override
    public String toString() {
        return "ModelMesh{" +
                "name='" + name + '\'' +
                ", subMeshCount=" + subMeshInstances.size() +
                ", boneCount=" + bones.size() +
                ", totalVertices=" + getTotalVertexCount() +
                ", totalIndices=" + getTotalIndexCount() +
                ", disposed=" + disposed +
                '}';
    }
}
