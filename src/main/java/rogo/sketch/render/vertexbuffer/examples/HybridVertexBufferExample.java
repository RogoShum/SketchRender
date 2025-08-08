package rogo.sketch.render.vertexbuffer.examples;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.vertexbuffer.HybridVertexBuffer;
import rogo.sketch.render.vertexbuffer.filler.DataFiller;

/**
 * Examples showing how to use the HybridVertexBuffer system
 */
public class HybridVertexBufferExample {

    /**
     * Example: Basic static mesh (similar to old StaticAttribute)
     */
    public static void staticMeshExample() {
        // Define vertex format for a basic mesh
        DataFormat meshFormat = DataFormat.builder("MeshVertex")
            .vec3Attribute("position")    // vertex position
            .vec3Attribute("normal")      // vertex normal
            .vec2Attribute("texCoord")    // texture coordinates
            .build();

        // Create static vertex buffer
        HybridVertexBuffer buffer = HybridVertexBuffer.createStatic(meshFormat, GL11.GL_TRIANGLES);

        // Fill static vertex data
        DataFiller filler = buffer.beginStaticFill(3); // Triangle with 3 vertices
        
        // First vertex
        filler.position(0.0f, 0.5f, 0.0f)
              .normal(0.0f, 0.0f, 1.0f)
              .texCoord(0.5f, 1.0f);
        
        // Second vertex
        filler.position(-0.5f, -0.5f, 0.0f)
              .normal(0.0f, 0.0f, 1.0f)
              .texCoord(0.0f, 0.0f);
        
        // Third vertex
        filler.position(0.5f, -0.5f, 0.0f)
              .normal(0.0f, 0.0f, 1.0f)
              .texCoord(1.0f, 0.0f);

        buffer.endStaticFill(3);

        // Draw the triangle
        buffer.draw();

        // Cleanup
        buffer.close();
    }

    /**
     * Example: Instanced rendering with static mesh and dynamic instance data
     */
    public static void instancedRenderingExample() {
        // Static format for the base mesh (a quad)
        DataFormat meshFormat = DataFormat.builder("QuadVertex")
            .vec3Attribute("position")
            .vec2Attribute("texCoord")
            .build();

        // Dynamic format for instance data
        DataFormat instanceFormat = DataFormat.builder("InstanceData")
            .mat4Attribute("instanceMatrix")  // transformation matrix per instance
            .vec4Attribute("instanceColor")   // color per instance
            .build();

        // Create hybrid vertex buffer
        HybridVertexBuffer buffer = HybridVertexBuffer.createHybrid(
            meshFormat, instanceFormat, GL11.GL_TRIANGLES);

        // Fill static vertex data (a quad)
        DataFiller meshFiller = buffer.beginStaticFill(6); // 2 triangles = 6 vertices
        
        // First triangle
        meshFiller.position(-0.5f, -0.5f, 0.0f).texCoord(0.0f, 0.0f);
        meshFiller.position(0.5f, -0.5f, 0.0f).texCoord(1.0f, 0.0f);
        meshFiller.position(-0.5f, 0.5f, 0.0f).texCoord(0.0f, 1.0f);
        
        // Second triangle
        meshFiller.position(0.5f, -0.5f, 0.0f).texCoord(1.0f, 0.0f);
        meshFiller.position(0.5f, 0.5f, 0.0f).texCoord(1.0f, 1.0f);
        meshFiller.position(-0.5f, 0.5f, 0.0f).texCoord(0.0f, 1.0f);

        buffer.endStaticFill(6);

        // Add multiple instances with different transformations and colors
        for (int i = 0; i < 10; i++) {
            DataFiller instanceFiller = buffer.addInstance();
            
            // Create transformation matrix
            Matrix4f transform = new Matrix4f()
                .translation(i * 2.0f, 0.0f, 0.0f)
                .rotateZ((float) Math.toRadians(i * 36));
            
            // Random color
            Vector4f color = new Vector4f(
                (float) Math.random(),
                (float) Math.random(), 
                (float) Math.random(),
                1.0f
            );
            
            instanceFiller.mat4(transform)
                         .vec4f(color);
        }

        buffer.endInstanceFill();

        // Draw all instances
        buffer.draw();

        // Cleanup
        buffer.close();
    }

    /**
     * Example: Dynamic instance updates (similar to old DynamicAttribute)
     */
    public static void dynamicInstanceExample() {
        // Static format for a simple cube
        DataFormat cubeFormat = DataFormat.builder("CubeVertex")
            .vec3Attribute("position")
            .vec3Attribute("normal")
            .build();

        // Instance format for dynamic positioning
        DataFormat instanceFormat = DataFormat.builder("DynamicInstance")
            .vec3Attribute("instancePosition")
            .floatAttribute("instanceScale")
            .vec4Attribute("instanceColor")
            .build();

        HybridVertexBuffer buffer = HybridVertexBuffer.createHybrid(
            cubeFormat, instanceFormat, GL11.GL_TRIANGLES);

        // Fill static cube data (simplified - just one face)
        DataFiller cubeFiller = buffer.beginStaticFill(6);
        
        // Front face (2 triangles)
        cubeFiller.position(-0.5f, -0.5f, 0.5f).normal(0.0f, 0.0f, 1.0f);
        cubeFiller.position(0.5f, -0.5f, 0.5f).normal(0.0f, 0.0f, 1.0f);
        cubeFiller.position(-0.5f, 0.5f, 0.5f).normal(0.0f, 0.0f, 1.0f);
        
        cubeFiller.position(0.5f, -0.5f, 0.5f).normal(0.0f, 0.0f, 1.0f);
        cubeFiller.position(0.5f, 0.5f, 0.5f).normal(0.0f, 0.0f, 1.0f);
        cubeFiller.position(-0.5f, 0.5f, 0.5f).normal(0.0f, 0.0f, 1.0f);

        buffer.endStaticFill(6);

        // Simulate animation loop
        for (int frame = 0; frame < 60; frame++) {
            // Clear previous instances
            buffer.clearInstances();
            
            // Add animated instances
            for (int i = 0; i < 5; i++) {
                DataFiller instanceFiller = buffer.addInstance();
                
                float time = frame * 0.1f;
                Vector3f position = new Vector3f(
                    i * 2.0f,
                    (float) Math.sin(time + i) * 2.0f,
                    0.0f
                );
                
                float scale = 0.5f + (float) Math.sin(time + i * 0.5f) * 0.3f;
                
                Vector4f color = new Vector4f(
                    (float) Math.sin(time + i) * 0.5f + 0.5f,
                    (float) Math.cos(time + i) * 0.5f + 0.5f,
                    0.5f,
                    1.0f
                );
                
                instanceFiller.vec3f(position)
                             .floatValue(scale)
                             .vec4f(color);
            }
            
            buffer.endInstanceFill();
            
            // Draw frame
            buffer.draw();
        }

        buffer.close();
    }

    /**
     * Example: Compatibility checking with shader formats
     */
    public static void compatibilityExample() {
        // Define vertex buffer format
        DataFormat bufferFormat = DataFormat.builder("BufferFormat")
            .vec3Attribute("position")
            .vec3Attribute("normal")
            .vec2Attribute("texCoord")
            .build();

        // Define expected shader format
        DataFormat shaderFormat = DataFormat.builder("ShaderFormat")
            .add("a_position", rogo.sketch.render.shader.uniform.DataType.VEC3)
            .add("a_normal", rogo.sketch.render.shader.uniform.DataType.VEC3)
            .add("a_texCoord", rogo.sketch.render.shader.uniform.DataType.VEC2)
            .build();

        HybridVertexBuffer buffer = HybridVertexBuffer.createStatic(bufferFormat, GL11.GL_TRIANGLES);

        // Check compatibility
        if (buffer.isStaticCompatibleWith(shaderFormat)) {
            System.out.println("Buffer format is compatible with shader!");
            // Proceed with rendering
        } else {
            System.out.println("Format mismatch detected!");
            // Handle error or convert format
        }

        buffer.close();
    }
}
