package rogo.example;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import rogo.sketch.render.shader.ComputeShaderProgram;
import rogo.sketch.render.shader.GraphicsShaderProgram;
import rogo.sketch.render.shader.ShaderType;
import rogo.sketch.util.Identifier;
import rogo.sketch.vanilla.ShaderUtils;

import java.util.Map;

/**
 * Examples showing how to use shaders with Minecraft resource loading
 */
public class MinecraftShaderExamples {

    /**
     * Example: Load a compute shader from Minecraft resources
     */
    public static void loadComputeShaderFromResources(ResourceProvider resourceProvider) throws Exception {
        // This will load from assets/sketchrender/shaders/compute/particle_sim.comp
        ComputeShaderProgram computeShader = ShaderUtils.loadComputeShader(
            resourceProvider,
            new ResourceLocation("sketchrender", "particle_sim")
        );

        System.out.println("Loaded compute shader: " + computeShader.getIdentifier());
        
        computeShader.close();
    }

    /**
     * Example: Load a graphics shader from Minecraft resources
     */
    public static void loadGraphicsShaderFromResources(ResourceProvider resourceProvider) throws Exception {
        // This will load:
        // - assets/sketchrender/shaders/graphics/basic_mesh.vert
        // - assets/sketchrender/shaders/graphics/basic_mesh.frag
        GraphicsShaderProgram graphicsShader = ShaderUtils.loadSimpleGraphicsShader(
            resourceProvider,
            new ResourceLocation("sketchrender", "basic_mesh"),
            "basic_mesh", // vertex shader name
            "basic_mesh"  // fragment shader name
        );

        System.out.println("Loaded graphics shader: " + graphicsShader.getIdentifier());
        System.out.println("Vertex format: " + graphicsShader.getVertexFormat());
        
        graphicsShader.close();
    }

    /**
     * Example: Load an advanced graphics shader with multiple stages
     */
    public static void loadAdvancedGraphicsShader(ResourceProvider resourceProvider) throws Exception {
        GraphicsShaderProgram shader = ShaderUtils.graphicsBuilder(resourceProvider, new ResourceLocation("sketchrender", "terrain"))
            .vertex("terrain_vertex")           // loads terrain_vertex.vert
            .tessControl("terrain_tess_ctrl")   // loads terrain_tess_ctrl.tesc
            .tessEvaluation("terrain_tess_eval") // loads terrain_tess_eval.tese
            .geometry("terrain_geometry")       // loads terrain_geometry.geom
            .fragment("terrain_fragment")       // loads terrain_fragment.frag
            .build();

        System.out.println("Loaded advanced shader: " + shader.getIdentifier());
        
        shader.close();
    }

    /**
     * Example: Mix resource loading with inline GLSL
     */
    public static void mixedShaderLoading(ResourceProvider resourceProvider) throws Exception {
        String inlineFragmentShader = """
            #version 330 core
            in vec3 fragColor;
            out vec4 finalColor;
            
            uniform float time;
            
            void main() {
                float pulse = 0.5 + 0.5 * sin(time * 2.0);
                finalColor = vec4(fragColor * pulse, 1.0);
            }
            """;

        GraphicsShaderProgram shader = ShaderUtils.graphicsBuilder(resourceProvider, new ResourceLocation("sketchrender", "mixed"))
            .vertex("simple_vertex")        // Load from resource
            .fragment(inlineFragmentShader) // Use inline GLSL
            .build();

        System.out.println("Created mixed shader: " + shader.getIdentifier());
        
        shader.close();
    }

    /**
     * Example: Load shader with custom directory structure
     */
    public static void customDirectoryExample(ResourceProvider resourceProvider) throws Exception {
        // For more complex loading scenarios, you can implement custom logic
        Map<ShaderType, String> shaderSources = Map.of(
            ShaderType.VERTEX, loadCustomShader(resourceProvider, "custom/path/vertex_shader.glsl"),
            ShaderType.FRAGMENT, loadCustomShader(resourceProvider, "custom/path/fragment_shader.glsl")
        );

        GraphicsShaderProgram shader = new GraphicsShaderProgram(Identifier.of("custom_shader"), shaderSources);
        
        System.out.println("Created custom shader: " + shader.getIdentifier());
        
        shader.close();
    }

    /**
     * Helper method to load a shader from a custom path
     */
    private static String loadCustomShader(ResourceProvider resourceProvider, String path) throws Exception {
        ResourceLocation location = new ResourceLocation("sketchrender", path);
        return resourceProvider.openAsReader(location).lines()
            .reduce("", (a, b) -> a + "\n" + b);
    }

    /**
     * Example: Using the default naming convention
     */
    public static void defaultNamingExample(ResourceProvider resourceProvider) throws Exception {
        // This will automatically load:
        // - assets/sketchrender/shaders/graphics/my_shader_vertex.vert
        // - assets/sketchrender/shaders/graphics/my_shader_fragment.frag
        GraphicsShaderProgram shader = ShaderUtils.loadDefaultGraphicsShader(
            resourceProvider,
            new ResourceLocation("sketchrender", "my_shader")
        );

        System.out.println("Loaded shader with default naming: " + shader.getIdentifier());
        
        shader.close();
    }
}
