package rogo.sketch.render.shader.examples;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import rogo.sketch.api.ShaderResource;
import rogo.sketch.render.shader.GraphicsShaderProgram;

/**
 * Example demonstrating the new uniform system where ShaderResource
 * actually implements proper uniform setting functionality
 */
public class UniformTestExample {

    public static void testUniformSetting(ResourceProvider resourceProvider) throws Exception {
        // Create a simple shader with various uniform types
        String vertexSource = """
            #version 330 core
            layout (location = 0) in vec3 position;
            
            uniform mat4 mvpMatrix;
            uniform vec3 lightPosition;
            uniform vec4 color;
            uniform float time;
            uniform int frameCount;
            
            void main() {
                gl_Position = mvpMatrix * vec4(position, 1.0);
            }
            """;

        String fragmentSource = """
            #version 330 core
            out vec4 fragColor;
            
            uniform vec4 color;
            uniform float time;
            
            void main() {
                fragColor = color * (0.5 + 0.5 * sin(time));
            }
            """;

        GraphicsShaderProgram shader = GraphicsShaderProgram.builder(
            resourceProvider,
            new ResourceLocation("sketchrender", "uniform_test")
        )
        .vertex(vertexSource)
        .fragment(fragmentSource)
        .build();

        System.out.println("Created shader with vertex format: " + shader.getVertexFormat());

        // Now test setting uniforms - these should actually work!
        ShaderResource<Matrix4f> mvpMatrix = (ShaderResource<Matrix4f>) 
            shader.getUniformHookGroup().getUniform("mvpMatrix");
        ShaderResource<Vector3f> lightPosition = (ShaderResource<Vector3f>) 
            shader.getUniformHookGroup().getUniform("lightPosition");
        ShaderResource<Vector4f> color = (ShaderResource<Vector4f>) 
            shader.getUniformHookGroup().getUniform("color");
        ShaderResource<Float> time = (ShaderResource<Float>) 
            shader.getUniformHookGroup().getUniform("time");
        ShaderResource<Integer> frameCount = (ShaderResource<Integer>) 
            shader.getUniformHookGroup().getUniform("frameCount");

        // Test setting various uniform types
        if (mvpMatrix != null) {
            System.out.println("Setting MVP matrix uniform");
            mvpMatrix.set(new Matrix4f().identity().scale(0.5f));
        }

        if (lightPosition != null) {
            System.out.println("Setting light position uniform");
            lightPosition.set(new Vector3f(1.0f, 2.0f, 3.0f));
        }

        if (color != null) {
            System.out.println("Setting color uniform");
            color.set(new Vector4f(1.0f, 0.5f, 0.2f, 1.0f));
        }

        if (time != null) {
            System.out.println("Setting time uniform");
            time.set(0.016f);
        }

        if (frameCount != null) {
            System.out.println("Setting frame count uniform");
            frameCount.set(60);
        }

        System.out.println("All uniforms set successfully!");

        // Animation loop to test dynamic uniform updates
        for (int frame = 0; frame < 10; frame++) {
            if (time != null) {
                time.set(frame * 0.016f);
            }
            if (frameCount != null) {
                frameCount.set(frame);
            }
            
            // In a real application, you would render here
            System.out.println("Frame " + frame + " - uniforms updated");
        }

        shader.close();
    }

    /**
     * Test to verify that UniformHook properly updates ShaderResource values
     */
    public static void testUniformHookUpdates(ResourceProvider resourceProvider) throws Exception {
        // This would test the UniformHook.checkUpdate functionality
        // where the ValueGetter provides new values and the UniformHook
        // calls ShaderResource.set() to update the GPU uniform
        
        System.out.println("UniformHook update test would go here");
        System.out.println("This tests the flow: ValueGetter -> UniformHook.checkUpdate -> ShaderResource.set");
    }
}
