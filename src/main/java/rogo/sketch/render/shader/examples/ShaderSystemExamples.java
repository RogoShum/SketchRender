package rogo.sketch.render.shader.examples;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.shader.ComputeShaderProgram;
import rogo.sketch.render.shader.GraphicsShaderProgram;

import rogo.sketch.api.ShaderResource;
import rogo.sketch.render.vertexbuffer.HybridVertexBuffer;
import rogo.sketch.render.vertexbuffer.filler.DataFiller;
import rogo.sketch.render.vertexbuffer.filler.SSBOFiller;



/**
 * Examples showing how to use the modern shader system
 */
public class ShaderSystemExamples {

    /**
     * Example: Basic graphics shader with automatic vertex format detection
     */
    public static void basicGraphicsShaderExample(ResourceProvider resourceProvider) throws Exception {
        // Create a graphics shader program
        GraphicsShaderProgram shader = GraphicsShaderProgram.builder(
            resourceProvider, 
            new ResourceLocation("sketchrender", "basic_mesh")
        )
        .vertex("basic_vertex")     // loads shaders/graphics/basic_vertex.vert
        .fragment("basic_fragment") // loads shaders/graphics/basic_fragment.frag
        .build();

        // The shader automatically detected its vertex format
        DataFormat shaderFormat = shader.getVertexFormat();
        System.out.println("Shader expects vertex format: " + shaderFormat);

        // Create a compatible vertex buffer
        DataFormat bufferFormat = DataFormat.builder("MeshVertex")
            .vec3Attribute("position")
            .vec3Attribute("normal")
            .vec2Attribute("texCoord")
            .build();

        // Check compatibility
        if (shader.isCompatibleWith(bufferFormat)) {
            System.out.println("Vertex buffer is compatible with shader!");
            
            HybridVertexBuffer buffer = HybridVertexBuffer.createStatic(bufferFormat, GL11.GL_TRIANGLES);
            
            // Fill vertex data
            DataFiller filler = buffer.beginStaticFill(3);
            filler.position(0.0f, 0.5f, 0.0f).normal(0, 0, 1).texCoord(0.5f, 1.0f);
            filler.position(-0.5f, -0.5f, 0.0f).normal(0, 0, 1).texCoord(0.0f, 0.0f);
            filler.position(0.5f, -0.5f, 0.0f).normal(0, 0, 1).texCoord(1.0f, 0.0f);
            buffer.endStaticFill(3);

            // Set uniforms through UniformHookGroup
            ShaderResource<Matrix4f> mvpMatrix = (ShaderResource<Matrix4f>) shader.getUniformHookGroup().getUniform("mvpMatrix");
            ShaderResource<Vector4f> color = (ShaderResource<Vector4f>) shader.getUniformHookGroup().getUniform("color");
            
            if (mvpMatrix != null) {
                mvpMatrix.set(new Matrix4f().identity());
            }
            if (color != null) {
                color.set(new Vector4f(1.0f, 0.0f, 0.0f, 1.0f));
            }

            // Render
            shader.bind();
            buffer.draw();
            GraphicsShaderProgram.unbind();

            buffer.close();
        } else {
            GraphicsShaderProgram.CompatibilityReport report = shader.getCompatibilityReport(bufferFormat);
            System.out.println("Incompatible: " + report.getDetails());
        }

        shader.close();
    }

    /**
     * Example: Advanced graphics shader with geometry and tessellation
     */
    public static void advancedGraphicsShaderExample(ResourceProvider resourceProvider) throws Exception {
        GraphicsShaderProgram shader = GraphicsShaderProgram.builder(
            resourceProvider,
            new ResourceLocation("sketchrender", "advanced_mesh")
        )
        .vertex("terrain_vertex")
        .tessControl("terrain_tess_control")
        .tessEvaluation("terrain_tess_eval")
        .geometry("terrain_geometry")
        .fragment("terrain_fragment")
        .build();

        System.out.println("Advanced shader created with vertex format: " + shader.getVertexFormat());
        
        // Use with compatible vertex buffer...
        
        shader.close();
    }

    /**
     * Example: Compute shader for particle simulation
     */
    public static void computeShaderExample(ResourceProvider resourceProvider) throws Exception {
        // Create compute shader
        ComputeShaderProgram computeShader = ComputeShaderProgram.fromResource(
            resourceProvider,
            new ResourceLocation("sketchrender", "particle_simulation")
        );

        // Define particle data format
        DataFormat particleFormat = DataFormat.builder("Particle")
            .vec3Attribute("position")
            .vec3Attribute("velocity")
            .floatAttribute("life")
            .floatAttribute("mass")
            .build();

        // Create SSBO for particle data
        SSBOFiller particleBuffer = SSBOFiller.create(particleFormat, 10000);

        // Initialize particles
        for (int i = 0; i < 1000; i++) {
            particleBuffer.vertex(i)
                .position((float) Math.random() * 10 - 5, 0, (float) Math.random() * 10 - 5)
                .vec3f(0, 0, 0)  // velocity
                .floatValue(1.0f)  // life
                .floatValue(1.0f); // mass
        }
        particleBuffer.upload().bindToShaderSlot(0);

        // Set compute shader uniforms through UniformHookGroup
        ShaderResource<Float> deltaTime = (ShaderResource<Float>) computeShader.getUniformHookGroup().getUniform("deltaTime");
        ShaderResource<Vector3f> gravity = (ShaderResource<Vector3f>) computeShader.getUniformHookGroup().getUniform("gravity");
        
        if (deltaTime != null) {
            deltaTime.set(0.016f); // 60 FPS
        }
        if (gravity != null) {
            gravity.set(new Vector3f(0, -9.81f, 0));
        }

        // Run simulation
        computeShader.bind();
        computeShader.dispatch(1000 / 64 + 1); // 64 particles per work group
        computeShader.shaderStorageBarrier();

        computeShader.close();
    }

    /**
     * Example: Instanced rendering with graphics shader
     */
    public static void instancedRenderingExample(ResourceProvider resourceProvider) throws Exception {
        // Create shader for instanced rendering
        GraphicsShaderProgram shader = GraphicsShaderProgram.builder(
            resourceProvider,
            new ResourceLocation("sketchrender", "instanced_mesh")
        )
        .vertex("instanced_vertex")
        .fragment("instanced_fragment")
        .build();

        // The shader should expect both mesh and instance data
        DataFormat shaderFormat = shader.getVertexFormat();
        System.out.println("Instanced shader format: " + shaderFormat);

        // Create mesh format (static data)
        DataFormat meshFormat = DataFormat.builder("Mesh")
            .vec3Attribute("position")
            .vec3Attribute("normal")
            .build();

        // Create instance format (dynamic data)
        DataFormat instanceFormat = DataFormat.builder("Instance")
            .vec3Attribute("instancePosition")
            .vec4Attribute("instanceColor")
            .floatAttribute("instanceScale")
            .build();

        // Check if our formats match what the shader expects
        // Note: In practice, you'd need to match the attribute locations properly
        
        HybridVertexBuffer buffer = HybridVertexBuffer.createHybrid(
            meshFormat, instanceFormat, GL11.GL_TRIANGLES);

        // Fill mesh data (a simple quad)
        // Fill mesh data
        DataFiller meshFiller = buffer.beginStaticFill(6);
        meshFiller.position(-0.5f, -0.5f, 0.0f).normal(0, 0, 1);
        meshFiller.position(0.5f, -0.5f, 0.0f).normal(0, 0, 1);
        meshFiller.position(-0.5f, 0.5f, 0.0f).normal(0, 0, 1);
        meshFiller.position(0.5f, -0.5f, 0.0f).normal(0, 0, 1);
        meshFiller.position(0.5f, 0.5f, 0.0f).normal(0, 0, 1);
        meshFiller.position(-0.5f, 0.5f, 0.0f).normal(0, 0, 1);
        buffer.endStaticFill(6);

        // Add instances
        for (int i = 0; i < 100; i++) {
            DataFiller instanceFiller = buffer.addInstance();
            instanceFiller.vec3f(i * 2.0f, 0, 0)  // position
                         .vec4f(1, 0, 0, 1)       // color
                         .floatValue(1.0f);       // scale
        }
        buffer.endInstanceFill();

        // Render
        shader.bind();
        buffer.draw();
        GraphicsShaderProgram.unbind();

        buffer.close();
        shader.close();
    }

    /**
     * Example: Uniform management and updating
     */
    public static void uniformManagementExample(ResourceProvider resourceProvider) throws Exception {
        GraphicsShaderProgram shader = GraphicsShaderProgram.create(
            resourceProvider,
            new ResourceLocation("sketchrender", "uniform_test"),
            "uniform_vertex",
            "uniform_fragment"
        );

        // Print all available uniforms would need to be done via UniformHookGroup
        System.out.println("Available uniforms via UniformHookGroup:");

        // Get specific uniforms through UniformHookGroup
        ShaderResource<Matrix4f> modelMatrix = (ShaderResource<Matrix4f>) shader.getUniformHookGroup().getUniform("modelMatrix");
        ShaderResource<Matrix4f> viewMatrix = (ShaderResource<Matrix4f>) shader.getUniformHookGroup().getUniform("viewMatrix");
        ShaderResource<Matrix4f> projMatrix = (ShaderResource<Matrix4f>) shader.getUniformHookGroup().getUniform("projMatrix");
        ShaderResource<Vector4f> lightColor = (ShaderResource<Vector4f>) shader.getUniformHookGroup().getUniform("lightColor");
        ShaderResource<Float> time = (ShaderResource<Float>) shader.getUniformHookGroup().getUniform("time");

        // Set uniform values
        if (modelMatrix != null) {
            modelMatrix.set(new Matrix4f().identity());
        }
        if (viewMatrix != null) {
            viewMatrix.set(new Matrix4f().lookAt(0, 0, 5, 0, 0, 0, 0, 1, 0));
        }
        if (projMatrix != null) {
            projMatrix.set(new Matrix4f().perspective((float) Math.toRadians(45), 16f/9f, 0.1f, 100f));
        }
        if (lightColor != null) {
            lightColor.set(new Vector4f(1, 1, 1, 1));
        }

        // Animation loop
        for (int frame = 0; frame < 60; frame++) {
            if (time != null) {
                time.set(frame * 0.016f);
            }
            
            shader.bind();
            // ... render frame ...
        }

        shader.close();
    }

    /**
     * Example: Shader creation from source code
     */
    public static void sourceCodeShaderExample(ResourceProvider resourceProvider) throws Exception {
        String vertexSource = """
            #version 330 core
            layout (location = 0) in vec3 position;
            layout (location = 1) in vec3 color;
            
            uniform mat4 mvpMatrix;
            out vec3 fragColor;
            
            void main() {
                gl_Position = mvpMatrix * vec4(position, 1.0);
                fragColor = color;
            }
            """;

        String fragmentSource = """
            #version 330 core
            in vec3 fragColor;
            out vec4 finalColor;
            
            uniform float alpha;
            
            void main() {
                finalColor = vec4(fragColor, alpha);
            }
            """;

        GraphicsShaderProgram shader = GraphicsShaderProgram.builder(
            resourceProvider,
            new ResourceLocation("sketchrender", "inline_shader")
        )
        .vertex(vertexSource)
        .fragment(fragmentSource)
        .build();

        System.out.println("Inline shader created with format: " + shader.getVertexFormat());

        shader.close();
    }
}
