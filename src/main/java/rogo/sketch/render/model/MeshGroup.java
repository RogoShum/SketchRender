package rogo.sketch.render.model;

import rogo.sketch.api.ResourceObject;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.format.DataFormat;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a mesh with hierarchical bone structure and sub-meshes.
 * This is the logical representation that stores mesh, bone, and vertex information
 * before compilation to GPU resources.
 */
public class MeshGroup implements ResourceObject {
    private final String name;
    private final PrimitiveType primitiveType;

    // Bone hierarchy
    @Nullable
    private MeshBone rootBone;
    private final Map<String, MeshBone> bonesByName;
    private final Map<Integer, MeshBone> bonesById;
    private final List<MeshBone> allBones;

    // Sub-meshes
    private final List<SubMesh> subMeshes;
    private final Map<String, SubMesh> subMeshesByName;

    // Metadata
    private final Map<String, Object> metadata;

    // Resource management
    private boolean disposed = false;

    public MeshGroup(String name, PrimitiveType primitiveType) {
        this.name = name;
        this.primitiveType = primitiveType;
        this.bonesByName = new HashMap<>();
        this.bonesById = new HashMap<>();
        this.allBones = new ArrayList<>();
        this.subMeshes = new ArrayList<>();
        this.subMeshesByName = new HashMap<>();
        this.metadata = new HashMap<>();
    }

    // === Bone Management ===

    /**
     * Set the root bone of the mesh
     */
    public void setRootBone(MeshBone rootBone) {
        this.rootBone = rootBone;
        rebuildBoneIndexes();
    }

    /**
     * Add a bone to the mesh
     */
    public void addBone(MeshBone bone) {
        if (bonesByName.containsKey(bone.getName())) {
            throw new IllegalArgumentException("Bone with name '" + bone.getName() + "' already exists");
        }
        if (bonesById.containsKey(bone.getId())) {
            throw new IllegalArgumentException("Bone with id " + bone.getId() + " already exists");
        }

        allBones.add(bone);
        bonesByName.put(bone.getName(), bone);
        bonesById.put(bone.getId(), bone);

        // Set as root if it's the first bone added and has no parent
        if (rootBone == null && bone.isRoot()) {
            rootBone = bone;
        }
    }

    /**
     * Remove a bone from the mesh
     */
    public void removeBone(MeshBone bone) {
        allBones.remove(bone);
        bonesByName.remove(bone.getName());
        bonesById.remove(bone.getId());

        if (rootBone == bone) {
            rootBone = null;
        }

        // Remove bone references from sub-meshes
        for (SubMesh subMesh : subMeshes) {
            if (subMesh.getBone() == bone) {
                subMesh.bindToBone(null);
            }
        }
    }

    /**
     * Find a bone by name
     */
    @Nullable
    public MeshBone findBone(String name) {
        return bonesByName.get(name);
    }

    /**
     * Find a bone by ID
     */
    @Nullable
    public MeshBone findBone(int id) {
        return bonesById.get(id);
    }

    /**
     * Rebuild bone indexes after hierarchy changes
     */
    private void rebuildBoneIndexes() {
        bonesByName.clear();
        bonesById.clear();
        allBones.clear();

        if (rootBone != null) {
            collectBones(rootBone);
        }
    }

    private void collectBones(MeshBone bone) {
        allBones.add(bone);
        bonesByName.put(bone.getName(), bone);
        bonesById.put(bone.getId(), bone);

        for (MeshBone child : bone.getChildren()) {
            collectBones(child);
        }
    }

    // === Sub-Mesh Management ===

    /**
     * Add a sub-mesh to the mesh
     */
    public void addSubMesh(SubMesh subMesh) {
        if (subMeshesByName.containsKey(subMesh.getName())) {
            throw new IllegalArgumentException("SubMesh with name '" + subMesh.getName() + "' already exists");
        }

        // Update sub-mesh index count based on mesh's primitive type
        subMesh.updateIndexCount(primitiveType);

        subMeshes.add(subMesh);
        subMeshesByName.put(subMesh.getName(), subMesh);
    }

    /**
     * Remove a sub-mesh from the mesh
     */
    public void removeSubMesh(SubMesh subMesh) {
        subMeshes.remove(subMesh);
        subMeshesByName.remove(subMesh.getName());
    }

    /**
     * Find a sub-mesh by name
     */
    @Nullable
    public SubMesh findSubMesh(String name) {
        return subMeshesByName.get(name);
    }

    /**
     * Get sub-meshes bound to a specific bone
     */
    public List<SubMesh> getSubMeshesForBone(MeshBone bone) {
        List<SubMesh> result = new ArrayList<>();
        for (SubMesh subMesh : subMeshes) {
            if (subMesh.getBone() == bone) {
                result.add(subMesh);
            }
        }
        return result;
    }

    /**
     * Get unbound sub-meshes (not attached to any bone)
     */
    public List<SubMesh> getUnboundSubMeshes() {
        List<SubMesh> result = new ArrayList<>();
        for (SubMesh subMesh : subMeshes) {
            if (!subMesh.isBound()) {
                result.add(subMesh);
            }
        }
        return result;
    }

    // === Utility Methods ===

    /**
     * Create a new bone and add it to the mesh
     */
    public MeshBone createBone(String name, int id) {
        MeshBone bone = new MeshBone(name, id);
        addBone(bone);
        return bone;
    }

    /**
     * Create a new sub-mesh and add it to the mesh
     */
    public SubMesh createSubMesh(String name, int id, int vertexCount, DataFormat vertexFormat) {
        SubMesh subMesh = new SubMesh(name, id, vertexCount, vertexFormat);
        addSubMesh(subMesh);
        return subMesh;
    }

    /**
     * Validate the entire mesh structure
     */
    public boolean isValid() {
        // Check that all bones have unique names and IDs
        if (bonesByName.size() != allBones.size() || bonesById.size() != allBones.size()) {
            return false;
        }

        // Check that all sub-meshes have unique names
        if (subMeshesByName.size() != subMeshes.size()) {
            return false;
        }

        // Validate each sub-mesh
        for (SubMesh subMesh : subMeshes) {
            if (!subMesh.isValid()) {
                return false;
            }

            // Check that bound bones exist in this mesh
            if (subMesh.isBound() && !allBones.contains(subMesh.getBone())) {
                return false;
            }
        }

        // Check primitive type requirements
        int totalVertices = getTotalVertexCount();
        if (!primitiveType.isValidVertexCount(totalVertices)) {
            return false;
        }

        return true;
    }

    /**
     * Get total vertex count across all sub-meshes
     */
    public int getTotalVertexCount() {
        return subMeshes.stream().mapToInt(SubMesh::getVertexCount).sum();
    }

    /**
     * Get total index count across all sub-meshes
     */
    public int getTotalIndexCount() {
        return subMeshes.stream().mapToInt(SubMesh::getIndexCount).sum();
    }

    /**
     * Check if the mesh has any animated bones
     */
    public boolean hasAnimation() {
        return !allBones.isEmpty();
    }

    /**
     * Clear all mesh data
     */
    public void clear() {
        rootBone = null;
        allBones.clear();
        bonesByName.clear();
        bonesById.clear();
        subMeshes.clear();
        subMeshesByName.clear();
        metadata.clear();
    }

    // === Getters ===

    public String getName() {
        return name;
    }

    @Nullable
    public MeshBone getRootBone() {
        return rootBone;
    }

    public List<MeshBone> getAllBones() {
        return new ArrayList<>(allBones);
    }

    public List<SubMesh> getSubMeshes() {
        return new ArrayList<>(subMeshes);
    }

    public int getBoneCount() {
        return allBones.size();
    }

    public int getSubMeshCount() {
        return subMeshes.size();
    }

    public PrimitiveType getPrimitiveType() {
        return primitiveType;
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

    // === ResourceObject Implementation ===

    @Override
    public int getHandle() {
        return 0;
    }

    @Override
    public void dispose() {
        if (!disposed) {
            // Dispose any resources if needed
            disposed = true;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public String toString() {
        return "BakedMesh{" +
                "name='" + name + '\'' +
                ", primitiveType=" + primitiveType +
                ", boneCount=" + allBones.size() +
                ", subMeshCount=" + subMeshes.size() +
                ", totalVertices=" + getTotalVertexCount() +
                ", totalIndices=" + getTotalIndexCount() +
                '}';
    }
}