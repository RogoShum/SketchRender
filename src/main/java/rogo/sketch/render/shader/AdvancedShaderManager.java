package rogo.sketch.render.shader;

import net.minecraft.server.packs.resources.ResourceProvider;
import rogo.sketch.render.shader.config.ShaderConfiguration;
import rogo.sketch.render.shader.config.ShaderConfigurationManager;
import rogo.sketch.render.shader.preprocessor.*;
import rogo.sketch.util.Identifier;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced shader manager with preprocessing, recompilation, and dependency tracking
 */
public class AdvancedShaderManager {
    
    private static AdvancedShaderManager instance;
    
    private final ShaderFactory shaderFactory;
    private final Map<Identifier, RecompilableShader> shaders = new ConcurrentHashMap<>();
    private final Map<Identifier, Set<Identifier>> dependencyGraph = new ConcurrentHashMap<>();
    private final ShaderConfigurationManager configManager;
    
    private AdvancedShaderManager(ResourceProvider resourceProvider) {
        this.shaderFactory = new ShaderFactory(resourceProvider);
        this.configManager = ShaderConfigurationManager.getInstance();
    }
    
    public static void initialize(ResourceProvider resourceProvider) {
        if (instance != null) {
            instance.dispose();
        }
        instance = new AdvancedShaderManager(resourceProvider);
    }
    
    public static AdvancedShaderManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AdvancedShaderManager not initialized. Call initialize() first.");
        }
        return instance;
    }
    
    /**
     * Create and register a graphics shader
     */
    public RecompilableGraphicsShader createGraphicsShader(Identifier identifier,
                                                           String vertexSource,
                                                           String fragmentSource) throws IOException {
        RecompilableGraphicsShader shader = shaderFactory.createGraphicsShader(identifier, vertexSource, fragmentSource);
        registerShader(identifier, shader);
        return shader;
    }
    
    /**
     * Create and register a graphics shader with multiple stages
     */
    public RecompilableGraphicsShader createGraphicsShader(Identifier identifier,
                                                           Map<ShaderType, String> sources) throws IOException {
        RecompilableGraphicsShader shader = shaderFactory.createGraphicsShader(identifier, sources);
        registerShader(identifier, shader);
        return shader;
    }
    
    /**
     * Create and register a compute shader
     */
    public RecompilableComputeShader createComputeShader(Identifier identifier,
                                                         String computeSource) throws IOException {
        RecompilableComputeShader shader = shaderFactory.createComputeShader(identifier, computeSource);
        registerShader(identifier, shader);
        return shader;
    }
    
    /**
     * Load shader from resources
     */
    public RecompilableGraphicsShader loadGraphicsShader(Identifier identifier,
                                                         Identifier vertexResource,
                                                         Identifier fragmentResource) throws IOException {
        RecompilableGraphicsShader shader = shaderFactory.loadGraphicsShader(identifier, vertexResource, fragmentResource);
        registerShader(identifier, shader);
        return shader;
    }
    
    /**
     * Load compute shader from resources
     */
    public RecompilableComputeShader loadComputeShader(Identifier identifier,
                                                       Identifier computeResource) throws IOException {
        RecompilableComputeShader shader = shaderFactory.loadComputeShader(identifier, computeResource);
        registerShader(identifier, shader);
        return shader;
    }
    
    /**
     * Get a registered shader
     */
    @SuppressWarnings("unchecked")
    public <T extends RecompilableShader> T getShader(Identifier identifier, Class<T> type) {
        RecompilableShader shader = shaders.get(identifier);
        if (shader != null && type.isInstance(shader)) {
            return (T) shader;
        }
        return null;
    }
    
    /**
     * Get a registered shader (any type)
     */
    public RecompilableShader getShader(Identifier identifier) {
        return shaders.get(identifier);
    }
    
    /**
     * Check if a shader is registered
     */
    public boolean hasShader(Identifier identifier) {
        return shaders.containsKey(identifier);
    }
    
    /**
     * Unregister and dispose a shader
     */
    public void removeShader(Identifier identifier) {
        RecompilableShader shader = shaders.remove(identifier);
        if (shader != null) {
            shader.dispose();
            dependencyGraph.remove(identifier);
            configManager.removeConfiguration(identifier);
        }
    }
    
    /**
     * Set configuration for a shader
     */
    public void setShaderConfiguration(Identifier identifier, ShaderConfiguration config) {
        configManager.setConfiguration(identifier, config);
    }
    
    /**
     * Update configuration for a shader
     */
    public void updateShaderConfiguration(Identifier identifier, 
                                         java.util.function.Consumer<ShaderConfiguration> updater) {
        configManager.updateConfiguration(identifier, updater);
    }
    
    /**
     * Apply a configuration preset to a shader
     */
    public void applyPreset(Identifier identifier, String presetName) {
        ShaderConfiguration preset = ShaderConfigurationManager.createPreset(presetName);
        setShaderConfiguration(identifier, preset);
    }
    
    /**
     * Recompile all shaders that need it
     */
    public void recompileIfNeeded() {
        List<IOException> errors = new ArrayList<>();
        
        for (Map.Entry<Identifier, RecompilableShader> entry : shaders.entrySet()) {
            RecompilableShader shader = entry.getValue();
            if (shader.needsRecompilation()) {
                try {
                    shader.recompile();
                    System.out.println("Recompiled shader: " + entry.getKey());
                } catch (IOException e) {
                    errors.add(e);
                    System.err.println("Failed to recompile shader " + entry.getKey() + ": " + e.getMessage());
                }
            }
        }
        
        if (!errors.isEmpty()) {
            System.err.println("Encountered " + errors.size() + " shader recompilation errors");
        }
    }
    
    /**
     * Force recompile all shaders
     */
    public void forceRecompileAll() {
        List<IOException> errors = new ArrayList<>();
        
        for (Map.Entry<Identifier, RecompilableShader> entry : shaders.entrySet()) {
            try {
                entry.getValue().forceRecompile();
                System.out.println("Force recompiled shader: " + entry.getKey());
            } catch (IOException e) {
                errors.add(e);
                System.err.println("Failed to force recompile shader " + entry.getKey() + ": " + e.getMessage());
            }
        }
        
        if (!errors.isEmpty()) {
            System.err.println("Encountered " + errors.size() + " shader recompilation errors");
        }
    }
    
    /**
     * Get all registered shader identifiers
     */
    public Set<Identifier> getAllShaderIds() {
        return new HashSet<>(shaders.keySet());
    }
    
    /**
     * Get shaders that depend on a specific file
     */
    public Set<Identifier> getShadersUsingFile(Identifier fileId) {
        Set<Identifier> dependentShaders = new HashSet<>();
        
        for (Map.Entry<Identifier, Set<Identifier>> entry : dependencyGraph.entrySet()) {
            if (entry.getValue().contains(fileId)) {
                dependentShaders.add(entry.getKey());
            }
        }
        
        return dependentShaders;
    }
    
    /**
     * Recompile shaders that depend on a specific file
     */
    public void recompileDependentShaders(Identifier changedFile) {
        Set<Identifier> dependentShaders = getShadersUsingFile(changedFile);
        
        System.out.println("File " + changedFile + " changed, recompiling " + dependentShaders.size() + " dependent shaders");
        
        for (Identifier shaderId : dependentShaders) {
            RecompilableShader shader = shaders.get(shaderId);
            if (shader != null) {
                try {
                    shader.forceRecompile();
                    System.out.println("Recompiled dependent shader: " + shaderId);
                } catch (IOException e) {
                    System.err.println("Failed to recompile dependent shader " + shaderId + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Get statistics about managed shaders
     */
    public ShaderManagerStats getStats() {
        int totalShaders = shaders.size();
        int graphicsShaders = 0;
        int computeShaders = 0;
        int shadersNeedingRecompilation = 0;
        
        for (RecompilableShader shader : shaders.values()) {
            if (shader instanceof RecompilableGraphicsShader) {
                graphicsShaders++;
            } else if (shader instanceof RecompilableComputeShader) {
                computeShaders++;
            }
            
            if (shader.needsRecompilation()) {
                shadersNeedingRecompilation++;
            }
        }
        
        return new ShaderManagerStats(totalShaders, graphicsShaders, computeShaders, 
                                     shadersNeedingRecompilation, dependencyGraph.size());
    }
    
    /**
     * Dispose all shaders and clean up
     */
    public void dispose() {
        for (RecompilableShader shader : shaders.values()) {
            shader.dispose();
        }
        shaders.clear();
        dependencyGraph.clear();
        configManager.clearAll();
    }
    
    private void registerShader(Identifier identifier, RecompilableShader shader) {
        shaders.put(identifier, shader);
        dependencyGraph.put(identifier, shader.getDependencies());
    }
    
    /**
     * Statistics about the shader manager
     */
    public record ShaderManagerStats(
            int totalShaders,
            int graphicsShaders,
            int computeShaders,
            int shadersNeedingRecompilation,
            int totalDependencies
    ) {
        @Override
        public String toString() {
            return String.format(
                "ShaderManagerStats{total=%d, graphics=%d, compute=%d, needRecompile=%d, dependencies=%d}",
                totalShaders, graphicsShaders, computeShaders, shadersNeedingRecompilation, totalDependencies
            );
        }
    }
}
