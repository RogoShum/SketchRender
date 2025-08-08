package rogo.sketch.render.vertexbuffer.examples;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import rogo.sketch.render.vertexbuffer.ModernVertexBuffer;
import rogo.sketch.render.vertexbuffer.filler.ByteBufferFiller;
import rogo.sketch.render.vertexbuffer.filler.DataFiller;
import rogo.sketch.render.vertexbuffer.filler.MemoryFiller;
import rogo.sketch.render.vertexbuffer.filler.SSBOFiller;
import rogo.sketch.render.data.format.DataElement;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.shader.uniform.DataType;

/**
 * Examples showing how to use the modern vertex format system
 */
public class VertexFormatExamples {

    /**
     * Example: Basic vertex format for position, normal, and texture coordinates
     */
    public static void basicVertexFormatExample() {
        // Create a vertex format using the builder
        DataFormat format = DataFormat.builder("BasicVertex")
            .vec3Attribute("position")    // vec3 position at index 0
            .vec3Attribute("normal")      // vec3 normal at index 1  
            .vec2Attribute("texCoord")    // vec2 texture coordinate at index 2
            .build();

        // Create a vertex buffer
        ModernVertexBuffer vertexBuffer = ModernVertexBuffer.createStatic(format, 1000);

        // Fill vertex data using fluent interface
        DataFiller filler = vertexBuffer.beginFill();
        
        // Add first vertex
        filler.position(0.0f, 0.0f, 0.0f)
              .normal(0.0f, 1.0f, 0.0f)
              .texCoord(0.0f, 0.0f);

        // Add second vertex
        filler.position(1.0f, 0.0f, 0.0f)
              .normal(0.0f, 1.0f, 0.0f)
              .texCoord(1.0f, 0.0f);

        // Add third vertex
        filler.position(0.5f, 1.0f, 0.0f)
              .normal(0.0f, 1.0f, 0.0f)
              .texCoord(0.5f, 1.0f);

        // Finish and upload to GPU
        vertexBuffer.endFill(3);

        // Draw the triangle
        vertexBuffer.draw(GL11.GL_TRIANGLES);

        // Cleanup
        vertexBuffer.close();
    }

    /**
     * Example: Complex vertex format with various data types
     */
    public static void complexVertexFormatExample() {
        DataFormat format = DataFormat.builder("ComplexVertex")
            .vec3Attribute("position")      // vec3 position
            .vec3Attribute("normal")        // vec3 normal
            .vec2Attribute("texCoord")      // vec2 texture coordinate
            .add("color", DataType.VEC4UB, true)  // normalized RGBA color bytes
            .add("boneIndices", DataType.VEC4I)   // bone indices for skeletal animation
            .add("boneWeights", DataType.VEC4)    // bone weights
            .build();

        ModernVertexBuffer vertexBuffer = ModernVertexBuffer.createDynamic(format, 500);
        DataFiller filler = vertexBuffer.beginFill();

        // Fill with complex data
        filler.vec3f(0.0f, 0.0f, 0.0f)     // position
              .vec3f(0.0f, 1.0f, 0.0f)     // normal
              .vec2f(0.0f, 0.0f)           // texCoord
              .vec4ub(255, 128, 64, 255)   // color (RGBA bytes)
              .vec4i(0, 1, 2, 3)           // boneIndices
              .vec4f(0.5f, 0.3f, 0.2f, 0.0f); // boneWeights

        vertexBuffer.endFill(1);
        vertexBuffer.close();
    }

    /**
     * Example: Using SSBO for large datasets with random access
     */
    public static void ssboExample() {
        // Define format for particle data
        DataFormat particleFormat = DataFormat.builder("Particle")
            .vec3Attribute("position")
            .vec3Attribute("velocity") 
            .floatAttribute("life")
            .floatAttribute("size")
            .vec4Attribute("color")
            .build();

        // Create SSBO filler for 100,000 particles
        SSBOFiller ssboFiller = SSBOFiller.create(particleFormat, 100000);

        // Fill particle data with random access (can fill any particle in any order)
        ssboFiller.vertex(42)  // Jump to particle 42
                  .position(1.0f, 2.0f, 3.0f)
                  .floatValue(1.0f)  // life
                  .floatValue(2.5f)  // size
                  .color(1.0f, 0.5f, 0.0f, 1.0f);

        ssboFiller.vertex(0)   // Jump to particle 0
                  .position(0.0f, 0.0f, 0.0f)
                  .floatValue(0.0f)
                  .floatValue(1.0f)
                  .color(1.0f, 1.0f, 1.0f, 1.0f);

        // Upload to GPU and bind to shader slot
        ssboFiller.upload()
                  .bindToShaderSlot(0);

        // SSBO is now ready for use in compute shaders
    }

    /**
     * Example: Direct memory manipulation for high-performance scenarios
     */
    public static void memoryFillerExample() {
        DataFormat format = DataFormat.builder("HighPerf")
            .vec3Attribute("position")
            .vec4Attribute("color")
            .build();

        // Allocate memory directly
        MemoryFiller memoryFiller = MemoryFiller.allocate(format, 1000);

        // Fill data with maximum performance
        for (int i = 0; i < 1000; i++) {
            memoryFiller.vertex(i)
                        .vec3f(i * 0.1f, 0.0f, 0.0f)
                        .vec4f(1.0f, 0.0f, 0.0f, 1.0f);
        }

        // Copy to another memory location or use directly
        // memoryFiller.copyTo(otherAddress, memoryFiller.getCapacity());

        // Remember to dispose when done
        memoryFiller.dispose();
    }

    /**
     * Example: ByteBuffer integration for existing code
     */
    public static void byteBufferExample() {
        DataFormat format = DataFormat.builder("Legacy")
            .vec3Attribute("position")
            .vec2Attribute("texCoord")
            .build();

        // Create with existing ByteBuffer
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(format.getStride() * 100);
        ByteBufferFiller filler = ByteBufferFiller.wrap(format, buffer);

        // Fill using fluent interface
        filler.vec3f(1.0f, 2.0f, 3.0f)
              .vec2f(0.5f, 0.5f);

        // Get buffer ready for OpenGL
        java.nio.ByteBuffer readyBuffer = filler.prepareForReading();
        
        // Use with OpenGL calls...
    }

    /**
     * Example: Format compatibility checking for shader matching
     */
    public static void formatCompatibilityExample() {
        // Vertex buffer format
        DataFormat vertexFormat = DataFormat.builder("Vertex")
            .vec3Attribute("position")
            .vec3Attribute("normal")
            .vec2Attribute("texCoord")
            .build();

        // Shader expected format (from shader reflection or manual definition)
        DataFormat shaderFormat = DataFormat.builder("Shader")
            .add("a_position", DataType.VEC3)   // position attribute in shader
            .add("a_normal", DataType.VEC3)     // normal attribute in shader
            .add("a_texCoord", DataType.VEC2)   // texture coordinate in shader
            .build();

        // Check compatibility
        if (vertexFormat.isCompatibleWith(shaderFormat)) {
            System.out.println("Vertex format is compatible with shader!");
            // Safe to use vertex buffer with this shader
        } else {
            System.out.println("Format mismatch! Cannot use this vertex buffer with shader.");
        }

        // Check exact match
        if (vertexFormat.matches(shaderFormat)) {
            System.out.println("Perfect format match!");
        }
    }

    /**
     * Example: Matrix data in vertex attributes (for instance matrices)
     */
    public static void matrixAttributeExample() {
        DataFormat instanceFormat = DataFormat.builder("InstanceData")
            .mat4Attribute("instanceMatrix")  // 4x4 transformation matrix per instance
            .vec4Attribute("instanceColor")   // color per instance
            .build();

        ModernVertexBuffer instanceBuffer = ModernVertexBuffer.createDynamic(instanceFormat, 1000);
        DataFiller filler = instanceBuffer.beginFill();

        // Fill instance data
        Matrix4f transform = new Matrix4f().translation(1.0f, 2.0f, 3.0f);
        Vector4f color = new Vector4f(1.0f, 0.5f, 0.0f, 1.0f);

        filler.mat4(transform)
              .vec4f(color);

        instanceBuffer.endFill(1);

        // Use for instanced rendering
        instanceBuffer.drawInstanced(GL11.GL_TRIANGLES, 100); // Draw 100 instances

        instanceBuffer.close();
    }
}