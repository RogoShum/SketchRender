package rogo.sketch.render.model;

import org.joml.Matrix4f;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.format.DataFormat;

/**
 * Builder utility class for creating Mesh objects programmatically.
 * Provides a fluent interface for constructing meshes with bones and sub-meshes.
 */
public class MeshBuilder {
    private final MeshGroup meshGroup;
    private MeshBone currentBone;
    private SubMesh currentSubMesh;
    
    public MeshBuilder(String name, PrimitiveType primitiveType) {
        this.meshGroup = new MeshGroup(name, primitiveType);
    }
    
    /**
     * Create a new mesh builder
     */
    public static MeshBuilder create(String name, PrimitiveType primitiveType) {
        return new MeshBuilder(name, primitiveType);
    }
    
    // === Bone Building ===
    
    /**
     * Add a root bone to the mesh
     */
    public MeshBuilder rootBone(String name, int id) {
        MeshBone bone = new MeshBone(name, id);
        meshGroup.addBone(bone);
        meshGroup.setRootBone(bone);
        currentBone = bone;
        return this;
    }
    
    /**
     * Add a child bone to the current bone
     */
    public MeshBuilder childBone(String name, int id) {
        if (currentBone == null) {
            throw new IllegalStateException("No parent bone selected");
        }
        
        MeshBone bone = new MeshBone(name, id);
        meshGroup.addBone(bone);
        currentBone.addChild(bone);
        currentBone = bone;
        return this;
    }
    
    /**
     * Add a bone with transform
     */
    public MeshBuilder boneWithTransform(String name, int id, Matrix4f localTransform, Matrix4f inverseBindPose) {
        MeshBone bone = new MeshBone(name, id, localTransform, inverseBindPose);
        meshGroup.addBone(bone);
        
        if (meshGroup.getRootBone() == null) {
            meshGroup.setRootBone(bone);
        }
        
        currentBone = bone;
        return this;
    }
    
    /**
     * Select an existing bone by name
     */
    public MeshBuilder selectBone(String name) {
        currentBone = meshGroup.findBone(name);
        if (currentBone == null) {
            throw new IllegalArgumentException("Bone not found: " + name);
        }
        return this;
    }
    
    /**
     * Go back to parent bone
     */
    public MeshBuilder parentBone() {
        if (currentBone != null && currentBone.getParent() != null) {
            currentBone = currentBone.getParent();
        }
        return this;
    }
    
    // === Sub-Mesh Building ===
    
    /**
     * Add a sub-mesh
     */
    public MeshBuilder subMesh(String name, int id, int vertexCount, DataFormat vertexFormat) {
        currentSubMesh = meshGroup.createSubMesh(name, id, vertexCount, vertexFormat);
        return this;
    }
    
    /**
     * Bind current sub-mesh to current bone
     */
    public MeshBuilder bindToBone() {
        if (currentSubMesh == null) {
            throw new IllegalStateException("No sub-mesh selected");
        }
        if (currentBone == null) {
            throw new IllegalStateException("No bone selected");
        }
        
        currentSubMesh.bindToBone(currentBone);
        return this;
    }
    
    /**
     * Bind current sub-mesh to a specific bone
     */
    public MeshBuilder bindToBone(String boneName) {
        if (currentSubMesh == null) {
            throw new IllegalStateException("No sub-mesh selected");
        }
        
        MeshBone bone = meshGroup.findBone(boneName);
        if (bone == null) {
            throw new IllegalArgumentException("Bone not found: " + boneName);
        }
        
        currentSubMesh.bindToBone(bone);
        return this;
    }
    
    /**
     * Add vertex data to current sub-mesh
     */
    public MeshBuilder vertices(float... vertexData) {
        if (currentSubMesh == null) {
            throw new IllegalStateException("No sub-mesh selected");
        }
        
        currentSubMesh.addVertex(vertexData);
        return this;
    }
    
    /**
     * Add index data to current sub-mesh
     */
    public MeshBuilder indices(int... indexData) {
        if (currentSubMesh == null) {
            throw new IllegalStateException("No sub-mesh selected");
        }
        
        currentSubMesh.addIndices(indexData);
        return this;
    }
    
    /**
     * Set material for current sub-mesh
     */
    public MeshBuilder material(String materialName) {
        if (currentSubMesh == null) {
            throw new IllegalStateException("No sub-mesh selected");
        }
        
        currentSubMesh.setMaterialName(materialName);
        return this;
    }
    
    /**
     * Set render priority for current sub-mesh
     */
    public MeshBuilder priority(int priority) {
        if (currentSubMesh == null) {
            throw new IllegalStateException("No sub-mesh selected");
        }
        
        currentSubMesh.setRenderPriority(priority);
        return this;
    }
    
    /**
     * Select an existing sub-mesh by name
     */
    public MeshBuilder selectSubMesh(String name) {
        currentSubMesh = meshGroup.findSubMesh(name);
        if (currentSubMesh == null) {
            throw new IllegalArgumentException("SubMesh not found: " + name);
        }
        return this;
    }
    
    // === Metadata ===
    
    /**
     * Add metadata to the mesh
     */
    public MeshBuilder metadata(String key, Object value) {
        meshGroup.setMetadata(key, value);
        return this;
    }
    
    // === Building ===
    
    /**
     * Build and return the mesh
     */
    public MeshGroup build() {
        if (!meshGroup.isValid()) {
            throw new IllegalStateException("Mesh validation failed");
        }
        return meshGroup;
    }
    
    /**
     * Build and immediately compile to ModelMesh
     */
    public ModelMesh buildAndCompile() {
        MeshGroup builtMeshGroup = build();
        return MeshCompiler.compile(builtMeshGroup);
    }
    
    /**
     * Build and compile with custom options
     */
    public ModelMesh buildAndCompile(MeshCompiler.CompilationOptions options) {
        MeshGroup builtMeshGroup = build();
        return MeshCompiler.compile(builtMeshGroup, options).getModelMesh();
    }
    
    // === Convenience Methods ===
    
    /**
     * Create a simple triangle mesh
     */
    public static MeshGroup createTriangle(String name, DataFormat format) {
        return MeshBuilder.create(name, PrimitiveType.TRIANGLES)
                .subMesh("triangle", 0, 3, format)
                .vertices(
                    -0.5f, -0.5f, 0.0f,  // Vertex 0
                     0.5f, -0.5f, 0.0f,  // Vertex 1
                     0.0f,  0.5f, 0.0f   // Vertex 2
                )
                .indices(0, 1, 2)
                .build();
    }
    
    /**
     * Create a simple quad mesh
     */
    public static MeshGroup createQuad(String name, DataFormat format) {
        return MeshBuilder.create(name, PrimitiveType.QUADS)
                .subMesh("quad", 0, 4, format)
                .vertices(
                    -0.5f, -0.5f, 0.0f,  // Bottom-left
                     0.5f, -0.5f, 0.0f,  // Bottom-right
                     0.5f,  0.5f, 0.0f,  // Top-right
                    -0.5f,  0.5f, 0.0f   // Top-left
                )
                .indices(0, 1, 2, 3)
                .build();
    }
    
    /**
     * Create a skeletal mesh with a simple bone hierarchy
     */
    public static MeshGroup createSkeletalExample(String name, DataFormat format) {
        return MeshBuilder.create(name, PrimitiveType.TRIANGLES)
                // Create bone hierarchy
                .rootBone("root", 0)
                .childBone("spine", 1)
                .childBone("head", 2)
                .parentBone() // Back to spine
                .childBone("arm_left", 3)
                .parentBone() // Back to spine
                .childBone("arm_right", 4)
                
                // Create sub-meshes bound to bones
                .selectBone("head")
                .subMesh("head_mesh", 0, 3, format)
                .vertices(
                    0.0f, 1.0f, 0.0f,   // Top of head
                    -0.2f, 0.8f, 0.0f,  // Left
                    0.2f, 0.8f, 0.0f    // Right
                )
                .indices(0, 1, 2)
                .bindToBone()
                
                .selectBone("arm_left")
                .subMesh("arm_left_mesh", 1, 4, format)
                .vertices(
                    -0.8f, 0.6f, 0.0f,   // Shoulder
                    -1.2f, 0.6f, 0.0f,   // Elbow
                    -1.2f, 0.4f, 0.0f,   // Elbow bottom
                    -0.8f, 0.4f, 0.0f    // Shoulder bottom
                )
                .indices(0, 1, 2, 3)
                .bindToBone()
                
                .build();
    }
}
