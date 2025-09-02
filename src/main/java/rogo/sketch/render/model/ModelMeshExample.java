package rogo.sketch.render.model;

import org.joml.Matrix4f;
import rogo.sketch.render.data.PrimitiveType;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.data.DataType;

/**
 * Example class demonstrating how to create and use Mesh and ModelMesh.
 * This shows the complete workflow from mesh creation to GPU compilation.
 */
public class ModelMeshExample {
    
    /**
     * Create a simple textured quad example
     */
    public static void createTexturedQuadExample() {
        // Define vertex format (position + UV coordinates)
        DataFormat vertexFormat = DataFormat.builder("PositionUV")
                .vec3Attribute("position")    // 3 floats for position
                .vec2Attribute("texCoord")    // 2 floats for UV
                .build();
        
        // Create mesh using builder
        Mesh mesh = MeshBuilder.create("textured_quad", PrimitiveType.QUADS)
                .subMesh("quad", 0, 4, vertexFormat)
                .vertices(
                    // position     texCoord
                    -1.0f, -1.0f, 0.0f,  0.0f, 0.0f,  // Bottom-left
                     1.0f, -1.0f, 0.0f,  1.0f, 0.0f,  // Bottom-right
                     1.0f,  1.0f, 0.0f,  1.0f, 1.0f,  // Top-right
                    -1.0f,  1.0f, 0.0f,  0.0f, 1.0f   // Top-left
                )
                .indices(0, 1, 2, 3)
                .material("grass_texture")
                .build();
        
        // Compile to ModelMesh
        ModelMesh modelMesh = MeshCompiler.compile(mesh, MeshCompiler.staticMeshOptions()).getModelMesh();
        
        // Use for rendering
        demonstrateRendering(modelMesh);
        
        // Clean up
        modelMesh.dispose();
    }
    
    /**
     * Create a skeletal character example
     */
    public static void createSkeletalCharacterExample() {
        // Define vertex format for skinned mesh (position + normal + UV + bone weights)
        DataFormat skinnedFormat = DataFormat.builder("SkinnedVertex")
                .vec3Attribute("position")
                .vec3Attribute("normal")
                .vec2Attribute("texCoord")
                .vec4Attribute("boneIndices")  // Up to 4 bone influences
                .vec4Attribute("boneWeights")  // Corresponding weights
                .build();
        
        // Create bone hierarchy
        Mesh characterMesh = MeshBuilder.create("character", PrimitiveType.QUADS)
                // Root bone
                .rootBone("root", 0)
                
                // Spine hierarchy
                .childBone("spine_base", 1)
                .childBone("spine_mid", 2)
                .childBone("spine_top", 3)
                
                // Head
                .childBone("neck", 4)
                .childBone("head", 5)
                
                // Left arm
                .selectBone("spine_top")
                .childBone("shoulder_L", 6)
                .childBone("upper_arm_L", 7)
                .childBone("lower_arm_L", 8)
                .childBone("hand_L", 9)
                
                // Right arm
                .selectBone("spine_top")
                .childBone("shoulder_R", 10)
                .childBone("upper_arm_R", 11)
                .childBone("lower_arm_R", 12)
                .childBone("hand_R", 13)
                
                // Left leg
                .selectBone("spine_base")
                .childBone("upper_leg_L", 14)
                .childBone("lower_leg_L", 15)
                .childBone("foot_L", 16)
                
                // Right leg
                .selectBone("spine_base")
                .childBone("upper_leg_R", 17)
                .childBone("lower_leg_R", 18)
                .childBone("foot_R", 19)
                
                // Create body mesh bound to spine
                .selectBone("spine_mid")
                .subMesh("torso", 0, 8, skinnedFormat)
                .vertices(
                    // Front face (simplified box)
                    -0.3f, -0.5f,  0.1f,  0.0f, 0.0f, 1.0f,  0.0f, 0.0f,  2.0f, 0.0f, 0.0f, 0.0f,  1.0f, 0.0f, 0.0f, 0.0f,
                     0.3f, -0.5f,  0.1f,  0.0f, 0.0f, 1.0f,  1.0f, 0.0f,  2.0f, 0.0f, 0.0f, 0.0f,  1.0f, 0.0f, 0.0f, 0.0f,
                     0.3f,  0.5f,  0.1f,  0.0f, 0.0f, 1.0f,  1.0f, 1.0f,  2.0f, 0.0f, 0.0f, 0.0f,  1.0f, 0.0f, 0.0f, 0.0f,
                    -0.3f,  0.5f,  0.1f,  0.0f, 0.0f, 1.0f,  0.0f, 1.0f,  2.0f, 0.0f, 0.0f, 0.0f,  1.0f, 0.0f, 0.0f, 0.0f,
                    // Back face
                    -0.3f, -0.5f, -0.1f,  0.0f, 0.0f, -1.0f, 1.0f, 0.0f,  2.0f, 0.0f, 0.0f, 0.0f,  1.0f, 0.0f, 0.0f, 0.0f,
                     0.3f, -0.5f, -0.1f,  0.0f, 0.0f, -1.0f, 0.0f, 0.0f,  2.0f, 0.0f, 0.0f, 0.0f,  1.0f, 0.0f, 0.0f, 0.0f,
                     0.3f,  0.5f, -0.1f,  0.0f, 0.0f, -1.0f, 0.0f, 1.0f,  2.0f, 0.0f, 0.0f, 0.0f,  1.0f, 0.0f, 0.0f, 0.0f,
                    -0.3f,  0.5f, -0.1f,  0.0f, 0.0f, -1.0f, 1.0f, 1.0f,  2.0f, 0.0f, 0.0f, 0.0f,  1.0f, 0.0f, 0.0f, 0.0f
                )
                .indices(
                    0, 1, 2, 3,  // Front
                    4, 5, 6, 7   // Back
                )
                .bindToBone()
                .material("character_body")
                
                // Create head mesh
                .selectBone("head")
                .subMesh("head", 1, 4, skinnedFormat)
                .vertices(
                    // Simple head quad
                    -0.15f, -0.15f, 0.05f,  0.0f, 0.0f, 1.0f,  0.0f, 0.0f,  5.0f, 0.0f, 0.0f, 0.0f,  1.0f, 0.0f, 0.0f, 0.0f,
                     0.15f, -0.15f, 0.05f,  0.0f, 0.0f, 1.0f,  1.0f, 0.0f,  5.0f, 0.0f, 0.0f, 0.0f,  1.0f, 0.0f, 0.0f, 0.0f,
                     0.15f,  0.15f, 0.05f,  0.0f, 0.0f, 1.0f,  1.0f, 1.0f,  5.0f, 0.0f, 0.0f, 0.0f,  1.0f, 0.0f, 0.0f, 0.0f,
                    -0.15f,  0.15f, 0.05f,  0.0f, 0.0f, 1.0f,  0.0f, 1.0f,  5.0f, 0.0f, 0.0f, 0.0f,  1.0f, 0.0f, 0.0f, 0.0f
                )
                .indices(0, 1, 2, 3)
                .bindToBone()
                .material("character_head")
                
                .metadata("character_type", "humanoid")
                .metadata("version", "1.0")
                .build();
        
        // Compile with animation support
        MeshCompiler.CompilationOptions options = MeshCompiler.defaultOptions()
                .setVertexUsage(rogo.sketch.render.data.Usage.DYNAMIC_DRAW); // For animation
        
        ModelMesh modelMesh = MeshCompiler.compile(characterMesh, options).getModelMesh();
        
        // Demonstrate animation
        demonstrateAnimation(modelMesh);
        
        // Clean up
        modelMesh.dispose();
    }
    
    /**
     * Demonstrate basic rendering workflow
     */
    private static void demonstrateRendering(ModelMesh modelMesh) {
        System.out.println("=== Rendering " + modelMesh.getName() + " ===");
        
        // Bind vertex resource
        modelMesh.bind();
        
        // Render each visible sub-mesh
        for (SubMeshInstance instance : modelMesh.getVisibleSubMeshes()) {
            System.out.println("Rendering sub-mesh: " + instance.getName());
            System.out.println("  Vertex offset: " + instance.getVertexOffset());
            System.out.println("  Vertex count: " + instance.getVertexCount());
            System.out.println("  Index offset: " + instance.getIndexOffset());
            System.out.println("  Index count: " + instance.getIndexCount());
            System.out.println("  Primitive type: " + instance.getPrimitiveType(modelMesh.getOriginalMesh()));
            System.out.println("  Material: " + instance.getMaterialName());
            
            // Here you would issue actual draw calls:
            // GL11.glDrawElements(...) or GL11.glDrawArrays(...)
        }
        
        // Unbind
        modelMesh.unbind();
        
        System.out.println("Total vertices: " + modelMesh.getTotalVertexCount());
        System.out.println("Total indices: " + modelMesh.getTotalIndexCount());
        System.out.println();
    }
    
    /**
     * Demonstrate animation workflow
     */
    private static void demonstrateAnimation(ModelMesh modelMesh) {
        System.out.println("=== Animating " + modelMesh.getName() + " ===");
        
        if (!modelMesh.hasAnimation()) {
            System.out.println("No bones for animation");
            return;
        }
        
        // Get bones
        MeshBone rootBone = modelMesh.getRootBone();
        MeshBone leftArm = modelMesh.findBone("upper_arm_L");
        MeshBone rightArm = modelMesh.findBone("upper_arm_R");
        
        if (leftArm != null) {
            // Animate left arm rotation
            Matrix4f armRotation = new Matrix4f()
                    .rotationZ((float) Math.toRadians(45)); // Rotate 45 degrees
            leftArm.setLocalTransform(armRotation);
            
            System.out.println("Animated left arm");
        }
        
        if (rightArm != null) {
            // Animate right arm rotation
            Matrix4f armRotation = new Matrix4f()
                    .rotationZ((float) Math.toRadians(-45)); // Rotate -45 degrees
            rightArm.setLocalTransform(armRotation);
            
            System.out.println("Animated right arm");
        }
        
        // Update bone transforms
        modelMesh.updateBoneTransforms();
        
        // Print bone matrices for shader upload
        System.out.println("Bone matrices for shader:");
        for (MeshBone bone : modelMesh.getBones()) {
            Matrix4f boneMatrix = bone.getBoneMatrix();
            System.out.println("  " + bone.getName() + ": " + boneMatrix);
        }
        
        System.out.println();
    }
    
    /**
     * Demonstrate batch rendering by primitive type
     */
    public static void demonstrateBatchRendering() {
        System.out.println("=== Batch Rendering Example ===");
        
        // Create a mesh with mixed primitive types
        DataFormat format = DataFormat.builder("PositionColor")
                .vec3Attribute("position")
                .vec3Attribute("color")
                .build();
        
        Mesh mixedMesh = MeshBuilder.create("mixed_primitives", PrimitiveType.TRIANGLES)
                .subMesh("triangles", 0, 6, format)
                .vertices(
                    // Triangle 1
                    0.0f, 0.0f, 0.0f,  1.0f, 0.0f, 0.0f,
                    1.0f, 0.0f, 0.0f,  0.0f, 1.0f, 0.0f,
                    0.5f, 1.0f, 0.0f,  0.0f, 0.0f, 1.0f,
                    // Triangle 2
                    2.0f, 0.0f, 0.0f,  1.0f, 1.0f, 0.0f,
                    3.0f, 0.0f, 0.0f,  1.0f, 0.0f, 1.0f,
                    2.5f, 1.0f, 0.0f,  0.0f, 1.0f, 1.0f
                )
                .indices(0, 1, 2, 3, 4, 5)
                
                .subMesh("quads", 1, 4, format)
                .vertices(
                    // Quad
                    4.0f, 0.0f, 0.0f,  1.0f, 0.5f, 0.0f,
                    5.0f, 0.0f, 0.0f,  0.5f, 1.0f, 0.0f,
                    5.0f, 1.0f, 0.0f,  0.0f, 1.0f, 0.5f,
                    4.0f, 1.0f, 0.0f,  0.5f, 0.0f, 1.0f
                )
                .indices(0, 1, 2, 3)
                
                .build();
        
        ModelMesh modelMesh = MeshCompiler.compile(mixedMesh);
        
        // Group sub-meshes by primitive type for batch rendering
        var subMeshesByType = modelMesh.getSubMeshesByPrimitiveType();
        
        modelMesh.bind();
        for (var entry : subMeshesByType.entrySet()) {
            PrimitiveType primitiveType = entry.getKey();
            var subMeshes = entry.getValue();
            
            System.out.println("Batch rendering " + subMeshes.size() + " sub-meshes of type: " + primitiveType);
            
            for (SubMeshInstance instance : subMeshes) {
                System.out.println("  " + instance.getName() + " at offset " + instance.getVertexOffset());
                // Issue draw call for this instance
            }
        }
        modelMesh.unbind();
        
        modelMesh.dispose();
        System.out.println();
    }
    
    /**
     * Main method demonstrating all examples
     */
    public static void main(String[] args) {
        System.out.println("ModelMesh Examples");
        System.out.println("==================");
        
        createTexturedQuadExample();
        createSkeletalCharacterExample();
        demonstrateBatchRendering();
        
        System.out.println("All examples completed successfully!");
    }
}
