package rogo.sketch.render.resource.buffer.examples;

import rogo.sketch.render.data.format.Std430DataFormat;
import rogo.sketch.render.resource.buffer.SSBOMemoryManager;
import rogo.sketch.render.resource.buffer.ModernShaderStorageBuffer;
import rogo.sketch.render.vertexbuffer.filler.SSBOFiller;

import org.lwjgl.opengl.GL15;

/**
 * Examples demonstrating SSBO management with std430 layouts and memory managers
 */
public class SSBOManagerExamples {
    
    /**
     * Example 1: Region-Pass-Section layout (like Minecraft chunk rendering)
     * Matches the structure used in your RegionMeshManager
     */
    public static void regionPassSectionExample() {
        // Define std430-compliant section data format
        Std430DataFormat sectionFormat = Std430DataFormat.std430Builder("SectionData")
            .intElement("mask")                    // 4 bytes, offset 0
            .intElement("visibility")              // 4 bytes, offset 4  
            // Array of 7 mesh structs would need special handling
            .build();
        
        // Create memory manager for Region-Pass-Section layout
        // 100 regions, 3 passes, 256 sections per region
        SSBOMemoryManager manager = SSBOMemoryManager.Factory
            .createRegionPassSectionLayout(sectionFormat, 100, 3, 256);
        
        // Register named blocks for easier access
        manager.registerBlock("region_0_pass_0", 0, 0)  // Region 0, Pass 0, all sections
               .registerBlock("region_0_pass_1", 0, 1)  // Region 0, Pass 1, all sections
               .registerBlock("region_5_pass_2", 5, 2); // Region 5, Pass 2, all sections
        
        // Fill specific section data
        SSBOFiller filler = manager.createFiller(0, 1, 42); // Region 0, Pass 1, Section 42
        filler.vertex(0)  // First (and only) data element in this section
              .intValue(0x7F)      // mask
              .intValue(1);        // visibility
        
        // Upload specific region-pass data
        manager.uploadBlock(manager.getBlock(0, 1)); // Upload Region 0, Pass 1
        
        // Bind to shader
        manager.bindToShaderSlot(0);
        
        // Cleanup
        manager.dispose();
    }
    
    /**
     * Example 2: Complex SSBO with multiple data types and std430 layout
     */
    public static void complexStd430Example() {
        // Define complex std430 format matching GLSL struct
        Std430DataFormat particleFormat = Std430DataFormat.std430Builder("ParticleData")
            .vec3Element("position")     // 12 bytes + 4 padding = 16 bytes total, offset 0
            .floatElement("life")        // 4 bytes, offset 16
            .vec3Element("velocity")     // 12 bytes + 4 padding = 16 bytes total, offset 32  
            .floatElement("mass")        // 4 bytes, offset 48
            .vec4Element("color")        // 16 bytes, offset 52 (but aligned to 64 for vec4)
            .build();
        
        // Validate std430 compliance
        System.out.println("std430 valid: " + particleFormat.validateStd430Layout());
        System.out.println("Stride: " + particleFormat.getStride());
        
        // Create modern SSBO with std430 format
        ModernShaderStorageBuffer ssbo = ModernShaderStorageBuffer
            .createStd430(particleFormat, 10000, GL15.GL_DYNAMIC_DRAW);
        
        // Fill data using fluent API
        ssbo.fill()
            .at(42)                           // Jump to particle 42
            .element(0).vec3f(1.0f, 2.0f, 3.0f)  // position
            .element(1).floatValue(5.0f)        // life
            .element(2).vec3f(0.1f, 0.2f, 0.3f)  // velocity
            .element(3).floatValue(2.5f)        // mass
            .element(4).vec4f(1.0f, 0.5f, 0.0f, 1.0f)  // color
            .upload();
        
        // Alternative: Fill sequentially
        ssbo.fillAt(100)
            .element(0).vec3f(0.0f, 0.0f, 0.0f)     // position
            .element(1).floatValue(0.0f)           // life  
            .element(2).vec3f(0.0f, 0.0f, 0.0f)     // velocity
            .element(3).floatValue(1.0f)           // mass
            .element(4).vec4f(1.0f, 1.0f, 1.0f, 1.0f)  // color
            .upload();
        
        // Bind to shader
        ssbo.bindShaderSlot(1);
        
        // Cleanup
        ssbo.dispose();
    }
    
    /**
     * Example 3: Multi-dimensional data management
     */
    public static void multiDimensionalExample() {
        // Create 3D volume data format
        Std430DataFormat voxelFormat = Std430DataFormat.std430Builder("VoxelData")
            .vec3Element("position")
            .floatElement("density")
            .intElement("material_id")
            .build();
        
        // Create 3D volume: 64x64x64 voxels
        SSBOMemoryManager volumeManager = SSBOMemoryManager.Factory
            .create3DVolume(voxelFormat, 64, 64, 64);
        
        // Fill specific voxel
        SSBOFiller voxelFiller = volumeManager.createFiller(10, 20, 30); // x=10, y=20, z=30
        voxelFiller.vertex(0)
                   .vec3f(10.5f, 20.5f, 30.5f)  // position
                   .floatValue(0.8f)            // density
                   .intValue(3);                // material_id
        
        // Upload entire Z-slice (all voxels with z=30)
        SSBOMemoryManager.MemoryBlock zSlice = volumeManager.getBlock(30);
        volumeManager.uploadBlock(zSlice);
        
        // Bind to compute shader
        volumeManager.bindToShaderSlot(2);
        
        volumeManager.dispose();
    }
    
    /**
     * Example 4: Dynamic SSBO with capacity management
     */
    public static void dynamicCapacityExample() {
        // Create format for dynamic particle system
        Std430DataFormat dynamicFormat = Std430DataFormat.std430Builder("DynamicParticle")
            .vec3Element("position")
            .vec3Element("velocity")
            .floatElement("life")
            .build();
        
        // Start with small capacity
        ModernShaderStorageBuffer dynamicSSBO = ModernShaderStorageBuffer
            .createStd430(dynamicFormat, 1000, GL15.GL_DYNAMIC_DRAW);
        
        // Simulate particle spawning - need more capacity
        dynamicSSBO.ensureCapacity(5000, true); // Preserve existing data
        
        // Fill new particles
        for (int i = 1000; i < 2000; i++) {
            dynamicSSBO.fillAt(i)
                       .element(0).vec3f(  // position
                           (float) Math.random() * 100,
                           (float) Math.random() * 100, 
                           (float) Math.random() * 100
                       )
                       .element(1).vec3f(  // velocity
                           (float) (Math.random() - 0.5) * 10,
                           (float) (Math.random() - 0.5) * 10,
                           (float) (Math.random() - 0.5) * 10
                       )
                       .element(2).floatValue(5.0f);  // life
        }
        
        // Upload all new particles at once
        dynamicSSBO.uploadRange(1000, 1000);
        
        dynamicSSBO.dispose();
    }
    
    /**
     * Example 5: Replicating your RegionMeshManager structure
     */
    public static void replicateRegionMeshExample() {
        // Define mesh data format similar to your SectionData struct
        Std430DataFormat meshDataFormat = Std430DataFormat.std430Builder("MeshData")
            .intElement("vertex_offset")    // SectionMesh.vertex_offset
            .intElement("element_count")    // SectionMesh.element_count  
            .intElement("index_offset")     // SectionMesh.index_offset
            .build();
        
        // Constants matching your system
        final int SECTION_COUNT = 256;
        final int PASS_COUNT = 3;
        final int FACE_COUNT = 7;
        
        // Create layout for Region -> Pass -> Section -> Face
        SSBOMemoryManager.MemoryLayout layout = new SSBOMemoryManager.MemoryLayout(
            100,          // Max regions
            PASS_COUNT,   // 3 passes (solid, cutout, translucent)
            SECTION_COUNT, // 256 sections per region
            FACE_COUNT    // 7 faces per section
        );
        
        SSBOMemoryManager meshManager = new SSBOMemoryManager(meshDataFormat, layout);
        
        // Fill specific mesh data: Region 5, Pass 1, Section 42, Face 3
        SSBOFiller meshFiller = meshManager.createFiller(5, 1, 42, 3);
        meshFiller.vertex(0)
                  .intValue(1000)  // vertex_offset
                  .intValue(36)    // element_count (12 triangles)
                  .intValue(500);  // index_offset
        
        // Upload specific section's data (all faces for Region 5, Pass 1, Section 42)
        SSBOMemoryManager.MemoryBlock sectionBlock = meshManager.getBlock(5, 1, 42);
        meshManager.uploadBlock(sectionBlock);
        
        // This would bind to your compute shader
        meshManager.bindToShaderSlot(0); // binding = 0 like in your GLSL
        
        meshManager.dispose();
    }
    
    /**
     * Main method to run all examples
     */
    public static void main(String[] args) {
        System.out.println("Running SSBO Manager Examples...");
        
        regionPassSectionExample();
        System.out.println("✓ Region-Pass-Section example completed");
        
        complexStd430Example();
        System.out.println("✓ Complex std430 example completed");
        
        multiDimensionalExample();
        System.out.println("✓ Multi-dimensional example completed");
        
        dynamicCapacityExample();
        System.out.println("✓ Dynamic capacity example completed");
        
        replicateRegionMeshExample();
        System.out.println("✓ RegionMesh replication example completed");
        
        System.out.println("All SSBO examples completed successfully!");
    }
}
