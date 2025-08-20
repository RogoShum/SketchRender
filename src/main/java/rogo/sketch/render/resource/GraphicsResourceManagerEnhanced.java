package rogo.sketch.render.resource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.packs.resources.ResourceProvider;
import rogo.sketch.api.ResourceObject;
import rogo.sketch.render.resource.loader.*;
import rogo.sketch.render.shader.*;
import rogo.sketch.render.shader.config.ShaderConfiguration;
import rogo.sketch.render.shader.config.ShaderConfigurationManager;
import rogo.sketch.render.shader.preprocessor.*;
import rogo.sketch.util.Identifier;

import java.io.BufferedReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Enhanced GraphicsResourceManager with advanced shader preprocessing support
 * 这个类扩展了现有的GraphicsResourceManager，添加了shader预处理功能
 */
public class GraphicsResourceManagerEnhanced extends GraphicsResourceManager {
    
    // Shader-specific management
    private final Map<Identifier, RecompilableShader> managedShaders = new ConcurrentHashMap<>();
    private final Map<Identifier, Set<Identifier>> shaderDependencies = new ConcurrentHashMap<>();
    private final ShaderConfigurationManager configManager;
    private final ShaderPreprocessor preprocessor;
    private final ShaderResourceProvider shaderResourceProvider;
    private ResourceProvider minecraftResourceProvider;
    
    // Enhanced shader loading flags
    private boolean enableShaderPreprocessing = true;
    private boolean enableAutoRecompilation = true;
    
    public GraphicsResourceManagerEnhanced() {
        super();
        this.configManager = ShaderConfigurationManager.getInstance();
        this.preprocessor = new ModernShaderPreprocessor();
        this.shaderResourceProvider = null; // Will be set when ResourceProvider is available
        
        // Register enhanced shader loader
        registerEnhancedShaderLoader();
    }
    
    /**
     * Initialize with Minecraft ResourceProvider for shader preprocessing
     */
    public void initializeShaderPreprocessing(ResourceProvider resourceProvider) {
        this.minecraftResourceProvider = resourceProvider;
        MinecraftShaderResourceProvider provider = new MinecraftShaderResourceProvider(resourceProvider);
        this.preprocessor.setResourceProvider(provider);
        
        // Update shader loader to use preprocessing
        registerLoader(ResourceTypes.SHADER_PROGRAM, 
                      new EnhancedShaderProgramLoader(resourceProvider, enableShaderPreprocessing));
    }
    
    /**
     * Enable or disable shader preprocessing
     */
    public void setShaderPreprocessingEnabled(boolean enabled) {
        this.enableShaderPreprocessing = enabled;
        if (minecraftResourceProvider != null) {
            registerLoader(ResourceTypes.SHADER_PROGRAM, 
                          new EnhancedShaderProgramLoader(minecraftResourceProvider, enabled));
        }
    }
    
    /**
     * Create a recompilable shader with preprocessing support
     */
    public RecompilableGraphicsShader createRecompilableGraphicsShader(Identifier identifier,
                                                                       Map<ShaderType, String> sources) {
        try {
            if (!enableShaderPreprocessing || shaderResourceProvider == null) {
                throw new IllegalStateException("Shader preprocessing not initialized");
            }
            
            RecompilableGraphicsShader shader = new RecompilableGraphicsShader(
                identifier, sources, preprocessor, shaderResourceProvider
            );
            
            // Register in both systems
            registerDirect(ResourceTypes.SHADER_PROGRAM, identifier, shader);
            managedShaders.put(identifier, shader);
            shaderDependencies.put(identifier, shader.getDependencies());
            
            return shader;
            
        } catch (Exception e) {
            System.err.println("Failed to create recompilable graphics shader: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Create a recompilable compute shader with preprocessing support
     */
    public RecompilableComputeShader createRecompilableComputeShader(Identifier identifier,
                                                                     String computeSource) {
        try {
            if (!enableShaderPreprocessing || shaderResourceProvider == null) {
                throw new IllegalStateException("Shader preprocessing not initialized");
            }
            
            RecompilableComputeShader shader = new RecompilableComputeShader(
                identifier, computeSource, preprocessor, shaderResourceProvider
            );
            
            // Register in both systems
            registerDirect(ResourceTypes.SHADER_PROGRAM, identifier, shader);
            managedShaders.put(identifier, shader);
            shaderDependencies.put(identifier, shader.getDependencies());
            
            return shader;
            
        } catch (Exception e) {
            System.err.println("Failed to create recompilable compute shader: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Set shader configuration
     */
    public void setShaderConfiguration(Identifier shaderId, ShaderConfiguration config) {
        configManager.setConfiguration(shaderId, config);
    }
    
    /**
     * Update shader configuration
     */
    public void updateShaderConfiguration(Identifier shaderId, 
                                         java.util.function.Consumer<ShaderConfiguration> updater) {
        configManager.updateConfiguration(shaderId, updater);
    }
    
    /**
     * Apply a configuration preset to a shader
     */
    public void applyShaderPreset(Identifier shaderId, String presetName) {
        ShaderConfiguration preset = ShaderConfigurationManager.createPreset(presetName);
        setShaderConfiguration(shaderId, preset);
    }
    
    /**
     * Get a managed recompilable shader
     */
    @SuppressWarnings("unchecked")
    public <T extends RecompilableShader> T getManagedShader(Identifier identifier, Class<T> type) {
        RecompilableShader shader = managedShaders.get(identifier);
        if (shader != null && type.isInstance(shader)) {
            return (T) shader;
        }
        return null;
    }
    
    /**
     * Recompile all shaders that need it
     */
    public void recompileShadersIfNeeded() {
        if (!enableAutoRecompilation) return;
        
        List<Exception> errors = new ArrayList<>();
        
        for (Map.Entry<Identifier, RecompilableShader> entry : managedShaders.entrySet()) {
            RecompilableShader shader = entry.getValue();
            if (shader.needsRecompilation()) {
                try {
                    shader.recompile();
                    // Update dependencies
                    shaderDependencies.put(entry.getKey(), shader.getDependencies());
                    System.out.println("Recompiled shader: " + entry.getKey());
                } catch (Exception e) {
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
     * Recompile shaders that depend on a specific file
     */
    public void recompileDependentShaders(Identifier changedFile) {
        Set<Identifier> dependentShaders = getShadersUsingFile(changedFile);
        
        System.out.println("File " + changedFile + " changed, recompiling " + dependentShaders.size() + " dependent shaders");
        
        for (Identifier shaderId : dependentShaders) {
            RecompilableShader shader = managedShaders.get(shaderId);
            if (shader != null) {
                try {
                    shader.forceRecompile();
                    shaderDependencies.put(shaderId, shader.getDependencies());
                    System.out.println("Recompiled dependent shader: " + shaderId);
                } catch (Exception e) {
                    System.err.println("Failed to recompile dependent shader " + shaderId + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Get shaders that depend on a specific file
     */
    public Set<Identifier> getShadersUsingFile(Identifier fileId) {
        Set<Identifier> dependentShaders = new HashSet<>();
        
        for (Map.Entry<Identifier, Set<Identifier>> entry : shaderDependencies.entrySet()) {
            if (entry.getValue().contains(fileId)) {
                dependentShaders.add(entry.getKey());
            }
        }
        
        return dependentShaders;
    }
    
    /**
     * Get shader management statistics
     */
    public ShaderStats getShaderStats() {
        int totalManaged = managedShaders.size();
        int graphicsShaders = 0;
        int computeShaders = 0;
        int needingRecompilation = 0;
        
        for (RecompilableShader shader : managedShaders.values()) {
            if (shader instanceof RecompilableGraphicsShader) {
                graphicsShaders++;
            } else if (shader instanceof RecompilableComputeShader) {
                computeShaders++;
            }
            
            if (shader.needsRecompilation()) {
                needingRecompilation++;
            }
        }
        
        return new ShaderStats(totalManaged, graphicsShaders, computeShaders, 
                              needingRecompilation, shaderDependencies.size());
    }
    
    @Override
    public void clearAllResources() {
        // Clean up managed shaders first
        for (RecompilableShader shader : managedShaders.values()) {
            try {
                shader.dispose();
            } catch (Exception e) {
                System.err.println("Error disposing managed shader: " + e.getMessage());
            }
        }
        managedShaders.clear();
        shaderDependencies.clear();
        
        // Clear configuration
        configManager.clearAll();
        
        // Call parent cleanup
        super.clearAllResources();
    }
    
    @Override
    public void dispose() {
        clearAllResources();
        super.dispose();
    }
    
    private void registerEnhancedShaderLoader() {
        // This will be properly set up when initializeShaderPreprocessing is called
        registerLoader(ResourceTypes.SHADER_PROGRAM, new ShaderProgramLoader());
    }
    
    /**
     * Statistics about shader management
     */
    public record ShaderStats(
            int totalManaged,
            int graphicsShaders,
            int computeShaders,
            int needingRecompilation,
            int totalDependencies
    ) {
        @Override
        public String toString() {
            return String.format(
                "ShaderStats{managed=%d, graphics=%d, compute=%d, needRecompile=%d, dependencies=%d}",
                totalManaged, graphicsShaders, computeShaders, needingRecompilation, totalDependencies
            );
        }
    }
}
