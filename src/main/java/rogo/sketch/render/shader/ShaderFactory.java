package rogo.sketch.render.shader;

import net.minecraft.server.packs.resources.ResourceProvider;
import rogo.sketch.render.shader.config.ShaderConfiguration;
import rogo.sketch.render.shader.config.ShaderConfigurationManager;
import rogo.sketch.render.shader.preprocessor.*;
import rogo.sketch.util.Identifier;

import rogo.sketch.api.ShaderProvider;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating shaders with optional preprocessing and recompilation support
 */
public class ShaderFactory {
    
    private final ShaderPreprocessor preprocessor;
    private final ShaderResourceProvider resourceProvider;
    private final boolean enableRecompilation;
    
    public ShaderFactory(ResourceProvider minecraftResourceProvider) {
        this(minecraftResourceProvider, true);
    }
    
    public ShaderFactory(ResourceProvider minecraftResourceProvider, boolean enableRecompilation) {
        this.enableRecompilation = enableRecompilation;
        if (enableRecompilation) {
            this.preprocessor = new ModernShaderPreprocessor();
            this.resourceProvider = new MinecraftShaderResourceProvider(minecraftResourceProvider);
            this.preprocessor.setResourceProvider(this.resourceProvider);
        } else {
            this.preprocessor = null;
            this.resourceProvider = null;
        }
    }
    
    public ShaderFactory(ShaderPreprocessor preprocessor, ShaderResourceProvider resourceProvider) {
        this.enableRecompilation = true;
        this.preprocessor = preprocessor;
        this.resourceProvider = resourceProvider;
        if (preprocessor != null) {
            this.preprocessor.setResourceProvider(resourceProvider);
        }
    }
    
    /**
     * Create a graphics shader (with optional recompilation support)
     * Returns GraphicsShader if recompilation disabled, ShaderAdapter if enabled
     */
    public ShaderProvider createGraphicsShader(Identifier identifier,
                                             Map<ShaderType, String> sources) throws IOException {
        if (enableRecompilation) {
            RecompilableShaderWrapper wrapper = createRecompilableGraphicsShader(identifier, sources);
            return new ShaderAdapter(wrapper);
        } else {
            return new GraphicsShader(identifier, sources);
        }
    }
    
    /**
     * Create a recompilable graphics shader wrapper
     */
    public RecompilableShaderWrapper createRecompilableGraphicsShader(Identifier identifier,
                                                                    Map<ShaderType, String> sources) throws IOException {
        if (!enableRecompilation) {
            throw new IllegalStateException("Recompilation is not enabled in this factory");
        }
        
        return new RecompilableShaderWrapper(
            identifier,
            sources,
            () -> {
                try {
                    return new GraphicsShader(identifier, sources);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create graphics shader", e);
                }
            },
            preprocessor,
            resourceProvider
        );
    }
    
    /**
     * Create a graphics shader with vertex and fragment sources
     */
    public ShaderProvider createGraphicsShader(Identifier identifier,
                                             String vertexSource,
                                             String fragmentSource) throws IOException {
        Map<ShaderType, String> sources = new HashMap<>();
        sources.put(ShaderType.VERTEX, vertexSource);
        sources.put(ShaderType.FRAGMENT, fragmentSource);
        return createGraphicsShader(identifier, sources);
    }
    
    /**
     * Create a compute shader (with optional recompilation support)
     * Returns ComputeShader if recompilation disabled, ShaderAdapter if enabled
     */
    public ShaderProvider createComputeShader(Identifier identifier,
                                            String computeSource) throws IOException {
        if (enableRecompilation) {
            RecompilableShaderWrapper wrapper = createRecompilableComputeShader(identifier, computeSource);
            return new ShaderAdapter(wrapper);
        } else {
            return new ComputeShader(identifier, computeSource);
        }
    }
    
    /**
     * Create a recompilable compute shader wrapper
     */
    public RecompilableShaderWrapper createRecompilableComputeShader(Identifier identifier,
                                                                   String computeSource) throws IOException {
        if (!enableRecompilation) {
            throw new IllegalStateException("Recompilation is not enabled in this factory");
        }
        
        Map<ShaderType, String> sources = Map.of(ShaderType.COMPUTE, computeSource);
        return new RecompilableShaderWrapper(
            identifier,
            sources,
            () -> {
                try {
                    return new ComputeShader(identifier, computeSource);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create compute shader", e);
                }
            },
            preprocessor,
            resourceProvider
        );
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
    public ShaderProvider loadGraphicsShader(Identifier identifier,
                                           Identifier vertexResource,
                                           Identifier fragmentResource) throws IOException {
        if (!enableRecompilation) {
            throw new IllegalStateException("Resource loading requires recompilation to be enabled");
        }
        String vertexSource = loadShaderResource(vertexResource);
        String fragmentSource = loadShaderResource(fragmentResource);
        return createGraphicsShader(identifier, vertexSource, fragmentSource);
    }
    
    /**
     * Load shader source from resource provider and create compute shader
     */
    public ShaderProvider loadComputeShader(Identifier identifier,
                                          Identifier computeResource) throws IOException {
        if (!enableRecompilation) {
            throw new IllegalStateException("Resource loading requires recompilation to be enabled");
        }
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
    
    /**
     * Check if recompilation is enabled
     */
    public boolean isRecompilationEnabled() {
        return enableRecompilation;
    }
    
    /**
     * Create a basic graphics shader without preprocessing
     */
    public GraphicsShader createBasicGraphicsShader(Identifier identifier, Map<ShaderType, String> sources) throws IOException {
        return new GraphicsShader(identifier, sources);
    }
    
    /**
     * Create a basic compute shader without preprocessing
     */
    public ComputeShader createBasicComputeShader(Identifier identifier, String computeSource) throws IOException {
        return new ComputeShader(identifier, computeSource);
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
