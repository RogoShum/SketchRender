package rogo.sketch.core.model;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.PrimitiveType;
import rogo.sketch.core.data.format.DataFormat;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a mesh with hierarchical bone structure and sub-meshes.
 * This container manages multiple PreparedMeshes and their relationships with bones.
 */
public class MeshGroup implements ResourceObject {
    private final String name;
    private final PrimitiveType primitiveType;
    private final DataFormat vertexFormat;

    // Meshes mapped by identifier/name
    private final Map<KeyId, PreparedMesh> meshes = new HashMap<>();
    // Ordered list for iteration or index-based access
    private final List<PreparedMesh> meshList = new ArrayList<>();

    // Bone hierarchy
    private final Map<String, MeshBone> bonesByName = new HashMap<>();
    private final Map<Integer, MeshBone> bonesById = new HashMap<>();
    private MeshBone rootBone;

    // Metadata
    private final Map<String, Object> metadata = new HashMap<>();
    private boolean disposed = false;

    public MeshGroup(String name, PrimitiveType primitiveType, DataFormat vertexFormat) {
        this.name = name;
        this.primitiveType = primitiveType;
        this.vertexFormat = vertexFormat;
    }

    /**
     * Add a mesh part to this group.
     */
    public void addMesh(KeyId name, PreparedMesh mesh) {
        if (!mesh.getVertexFormat().equals(this.vertexFormat)) {
            throw new IllegalArgumentException("Mesh vertex format mismatch. Expected: " + this.vertexFormat + ", Got: " + mesh.getVertexFormat());
        }
        if (mesh.getPrimitiveType() != this.primitiveType) {
            throw new IllegalArgumentException("Mesh primitive type mismatch. Expected: " + this.primitiveType + ", Got: " + mesh.getPrimitiveType());
        }
        meshes.put(name, mesh);
        meshList.add(mesh);
    }

    /**
     * Add a bone to the mesh group.
     */
    public void addBone(MeshBone bone) {
        bonesByName.put(bone.getName(), bone);
        bonesById.put(bone.getId(), bone);
        if (rootBone == null && bone.isRoot()) {
            rootBone = bone;
        }
    }

    /**
     * Set the root bone and rebuild the bone lookup maps.
     */
    public void setRootBone(MeshBone rootBone) {
        this.rootBone = rootBone;
        rebuildBoneIndexes();
    }

    private void rebuildBoneIndexes() {
        bonesByName.clear();
        bonesById.clear();
        if (rootBone != null) {
            collectBones(rootBone);
        }
    }

    private void collectBones(MeshBone bone) {
        bonesByName.put(bone.getName(), bone);
        bonesById.put(bone.getId(), bone);
        for (MeshBone child : bone.getChildren()) {
            collectBones(child);
        }
    }

    /**
     * Find a bone by name.
     */
    @Nullable
    public MeshBone findBone(String name) {
        return bonesByName.get(name);
    }

    @Nullable
    public PreparedMesh getMesh(KeyId name) {
        return meshes.get(name);
    }

    public Collection<PreparedMesh> getAllMeshes() {
        return Collections.unmodifiableList(meshList);
    }
    
    @Nullable
    public MeshBone getBone(String name) {
        return bonesByName.get(name);
    }
    
    @Nullable
    public MeshBone getBone(int id) {
        return bonesById.get(id);
    }

    @Nullable
    public MeshBone getRootBone() {
        return rootBone;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public int getTotalVertexCount() {
        return meshList.stream().mapToInt(PreparedMesh::getVertexCount).sum();
    }

    public int getTotalIndexCount() {
        return meshList.stream().mapToInt(PreparedMesh::getIndicesCount).sum();
    }

    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    @Nullable
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    public int getBoneCount() {
        return bonesByName.size();
    }

    public boolean hasAnimation() {
        return rootBone != null;
    }

    public int getSubMeshCount() {
        return meshList.size();
    }

    public List<PreparedMesh> getSubMeshes() {
        return Collections.unmodifiableList(meshList);
    }

    public boolean isValid() {
        return true; // Basic validation passed if constructed
    }

    public String getName() {
        return name;
    }

    public PrimitiveType getPrimitiveType() {
        return primitiveType;
    }

    public DataFormat getVertexFormat() {
        return vertexFormat;
    }

    @Override
    public int getHandle() {
        return 0;
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    public PrimitiveType primitiveType() {
        return primitiveType;
    }
}