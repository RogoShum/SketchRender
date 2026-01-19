package rogo.sketch.vanilla;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import rogo.sketch.render.shader.ComputeShader;
import rogo.sketch.render.shader.GraphicsShader;
import rogo.sketch.render.shader.ShaderType;
import rogo.sketch.util.KeyId;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for loading shaders from Minecraft resources
 * Bridges the gap between the generic Shader classes and Minecraft's resource system
 */
public class ShaderUtils {

    /**
     * Load a compute shader from Minecraft resources
     */
    public static ComputeShader loadComputeShader(ResourceProvider resourceProvider,
                                                  ResourceLocation shaderLocation) throws IOException {
        return loadComputeShader(resourceProvider, shaderLocation, shaderLocation.getPath());
    }

    /**
     * Load a compute shader from Minecraft resources with custom source name
     */
    public static ComputeShader loadComputeShader(ResourceProvider resourceProvider,
                                                  ResourceLocation shaderLocation,
                                                  String computeShaderSource) throws IOException {
        String shaderCode = loadShaderSource(resourceProvider, shaderLocation, "compute", computeShaderSource, ShaderType.COMPUTE);
        return new ComputeShader(KeyId.valueOf(shaderLocation), shaderCode);
    }

    /**
     * Load a graphics shader from Minecraft resources
     */
    public static GraphicsShader loadGraphicsShader(ResourceProvider resourceProvider,
                                                    ResourceLocation shaderLocation,
                                                    Map<ShaderType, String> shaderSources) throws IOException {
        Map<ShaderType, String> loadedSources = new HashMap<>();

        for (Map.Entry<ShaderType, String> entry : shaderSources.entrySet()) {
            ShaderType type = entry.getKey();
            String sourceName = entry.getValue();

            String shaderCode = loadShaderSource(resourceProvider, shaderLocation, "graphics", sourceName, type);
            loadedSources.put(type, shaderCode);
        }

        return new GraphicsShader(KeyId.valueOf(shaderLocation), loadedSources);
    }

    /**
     * Create a graphics shader builder that loads from Minecraft resources
     */
    public static GraphicsShaderBuilder graphicsBuilder(ResourceProvider resourceProvider, ResourceLocation shaderLocation) {
        return new GraphicsShaderBuilder(resourceProvider, shaderLocation);
    }

    /**
     * Load shader source from Minecraft resources
     */
    private static String loadShaderSource(ResourceProvider resourceProvider,
                                           ResourceLocation baseLocation,
                                           String shaderDirectory,
                                           String sourceName,
                                           ShaderType type) throws IOException {
        // Check if source is already GLSL code
        if (sourceName.contains("\n") || sourceName.contains("void main")) {
            return sourceName;
        }

        // Load from resource
        ResourceLocation shaderResource = new ResourceLocation(
                baseLocation.getNamespace(),
                "shaders/" + shaderDirectory + "/" + sourceName + "." + type.getExtension()
        );

        BufferedReader reader = resourceProvider.openAsReader(shaderResource);
        return reader.lines().collect(Collectors.joining("\n"));
    }

    /**
     * Builder for graphics shaders that loads from Minecraft resources
     */
    public static class GraphicsShaderBuilder {
        private final Map<ShaderType, String> shaderSources = new HashMap<>();
        private final ResourceProvider resourceProvider;
        private final ResourceLocation shaderLocation;

        public GraphicsShaderBuilder(ResourceProvider resourceProvider, ResourceLocation shaderLocation) {
            this.resourceProvider = resourceProvider;
            this.shaderLocation = shaderLocation;
        }

        public GraphicsShaderBuilder vertex(String source) {
            shaderSources.put(ShaderType.VERTEX, source);
            return this;
        }

        public GraphicsShaderBuilder fragment(String source) {
            shaderSources.put(ShaderType.FRAGMENT, source);
            return this;
        }

        public GraphicsShaderBuilder geometry(String source) {
            shaderSources.put(ShaderType.GEOMETRY, source);
            return this;
        }

        public GraphicsShaderBuilder tessControl(String source) {
            shaderSources.put(ShaderType.TESS_CONTROL, source);
            return this;
        }

        public GraphicsShaderBuilder tessEvaluation(String source) {
            shaderSources.put(ShaderType.TESS_EVALUATION, source);
            return this;
        }

        public GraphicsShader build() throws IOException {
            return loadGraphicsShader(resourceProvider, shaderLocation, shaderSources);
        }
    }

    /**
     * Load a simple graphics shader with vertex and fragment shaders
     */
    public static GraphicsShader loadSimpleGraphicsShader(ResourceProvider resourceProvider,
                                                          ResourceLocation shaderLocation,
                                                          String vertexShader,
                                                          String fragmentShader) throws IOException {
        return graphicsBuilder(resourceProvider, shaderLocation)
                .vertex(vertexShader)
                .fragment(fragmentShader)
                .build();
    }

    /**
     * Convenience method to load a shader where the vertex and fragment shader names match the location path
     */
    public static GraphicsShader loadDefaultGraphicsShader(ResourceProvider resourceProvider,
                                                           ResourceLocation shaderLocation) throws IOException {
        String baseName = shaderLocation.getPath();
        return loadSimpleGraphicsShader(resourceProvider, shaderLocation, baseName + "_vertex", baseName + "_fragment");
    }
}