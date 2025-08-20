package rogo.sketch.render.shader;

import net.minecraft.server.packs.resources.ResourceProvider;
import rogo.sketch.render.shader.config.ShaderConfiguration;
import rogo.sketch.render.shader.config.ShaderConfigurationManager;
import rogo.sketch.render.shader.preprocessor.*;
import rogo.sketch.util.Identifier;

import java.io.IOException;
import java.util.Map;

/**
 * Factory for creating shaders with preprocessing and recompilation support
 */
public class ShaderFactory {
    
    private final ShaderPreprocessor preprocessor;
    private final ShaderResourceProvider resourceProvider;
    
    public ShaderFactory(ResourceProvider minecraftResourceProvider) {
        this.preprocessor = new ModernShaderPreprocessor();
        this.resourceProvider = new MinecraftShaderResourceProvider(minecraftResourceProvider);
    }
    
    public ShaderFactory(ShaderPreprocessor preprocessor, ShaderResourceProvider resourceProvider) {
        this.preprocessor = preprocessor;
        this.resourceProvider = resourceProvider;
    }
    
    /**
     * Create a recompilable graphics shader
     */
    public RecompilableGraphicsShader createGraphicsShader(Identifier identifier,
                                                           Map<ShaderType, String> sources) throws IOException {
        return new RecompilableGraphicsShader(identifier, sources, preprocessor, resourceProvider);
    }
    
    /**
     * Create a recompilable graphics shader with vertex and fragment sources
     */
    public RecompilableGraphicsShader createGraphicsShader(Identifier identifier,
                                                           String vertexSource,
                                                           String fragmentSource) throws IOException {
        return RecompilableGraphicsShader.create(identifier, vertexSource, fragmentSource, preprocessor, resourceProvider);
    }
    
    /**
     * Create a recompilable compute shader
     */
    public RecompilableComputeShader createComputeShader(Identifier identifier,
                                                         String computeSource) throws IOException {
        return new RecompilableComputeShader(identifier, computeSource, preprocessor, resourceProvider);
    }
    
    /**
     * Create a traditional (non-recompilable) graphics shader
     */
    public GraphicsShader createStaticGraphicsShader(Identifier identifier,
                                                     Map<ShaderType, String> sources) throws IOException {
        // Preprocess sources once with current configuration
        Map<ShaderType, String> processedSources = preprocessSources(sources, identifier);
        return new GraphicsShader(identifier, processedSources);
    }
    
    /**
     * Create a traditional (non-recompilable) compute shader
     */
    public ComputeShader createStaticComputeShader(Identifier identifier,
                                                   String computeSource) throws IOException {
        // Preprocess source once with current configuration
        ShaderConfiguration config = ShaderConfigurationManager.getInstance().getConfiguration(identifier);
        preprocessor.setResourceProvider(resourceProvider);
        
        try {
            PreprocessorResult result = preprocessor.process(computeSource, identifier, config.getMacros());
            return new ComputeShader(identifier, result.processedSource());
        } catch (ShaderPreprocessorException e) {
            throw new IOException("Failed to preprocess compute shader: " + e.getMessage(), e);
        }
    }
    
    /**
     * Load shader sources from resource provider and create graphics shader
     */
    public RecompilableGraphicsShader loadGraphicsShader(Identifier identifier,
                                                         Identifier vertexResource,
                                                         Identifier fragmentResource) throws IOException {
        String vertexSource = loadShaderResource(vertexResource);
        String fragmentSource = loadShaderResource(fragmentResource);
        return createGraphicsShader(identifier, vertexSource, fragmentSource);
    }
    
    /**
     * Load shader source from resource provider and create compute shader
     */
    public RecompilableComputeShader loadComputeShader(Identifier identifier,
                                                       Identifier computeResource) throws IOException {
        String computeSource = loadShaderResource(computeResource);
        return createComputeShader(identifier, computeSource);
    }
    
    /**
     * Set configuration for a shader before creation
     */
    public ShaderFactory withConfiguration(Identifier shaderId, ShaderConfiguration config) {
        ShaderConfigurationManager.getInstance().setConfiguration(shaderId, config);
        return this;
    }
    
    /**
     * Set configuration using a builder pattern
     */
    public ShaderFactory withConfiguration(Identifier shaderId, 
                                          java.util.function.Consumer<ShaderConfiguration.Builder> configurer) {
        ShaderConfiguration.Builder builder = ShaderConfiguration.builder();
        configurer.accept(builder);
        return withConfiguration(shaderId, builder.build());
    }
    
    /**
     * Create factory with preset configuration
     */
    public static ShaderFactory withPreset(ResourceProvider resourceProvider, String presetName) {
        ShaderFactory factory = new ShaderFactory(resourceProvider);
        ShaderConfiguration preset = ShaderConfigurationManager.createPreset(presetName);
        ShaderConfigurationManager.getInstance().updateGlobalConfiguration(config -> {
            config.getMacros().putAll(preset.getMacros());
            config.getFeatures().addAll(preset.getFeatures());
            config.getProperties().putAll(preset.getProperties());
        });
        return factory;
    }
    
    private String loadShaderResource(Identifier resourceId) throws IOException {
        return resourceProvider.loadShaderSource(resourceId)
                .orElseThrow(() -> new IOException("Shader resource not found: " + resourceId));
    }
    
    private Map<ShaderType, String> preprocessSources(Map<ShaderType, String> sources, Identifier identifier) throws IOException {
        ShaderConfiguration config = ShaderConfigurationManager.getInstance().getConfiguration(identifier);
        preprocessor.setResourceProvider(resourceProvider);
        
        Map<ShaderType, String> processedSources = new java.util.HashMap<>();
        
        for (Map.Entry<ShaderType, String> entry : sources.entrySet()) {
            ShaderType type = entry.getKey();
            String source = entry.getValue();
            
            try {
                Identifier shaderTypeId = Identifier.of(identifier + "_" + type.name().toLowerCase());
                PreprocessorResult result = preprocessor.process(source, shaderTypeId, config.getMacros());
                processedSources.put(type, result.processedSource());
            } catch (ShaderPreprocessorException e) {
                throw new IOException("Failed to preprocess " + type.name().toLowerCase() + " shader: " + e.getMessage(), e);
            }
        }
        
        return processedSources;
    }
}
