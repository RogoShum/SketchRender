package rogo.sketch.render.shader;

import rogo.sketch.render.shader.config.ShaderConfiguration;
import rogo.sketch.render.shader.config.ShaderConfigurationManager;
import rogo.sketch.render.shader.preprocessor.*;
import rogo.sketch.util.Identifier;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Enhanced shader that supports recompilation with different configurations
 * and import dependency tracking
 */
public abstract class RecompilableShader extends Shader {
    
    private final Map<ShaderType, String> originalSources;
    private final ShaderPreprocessor preprocessor;
    private final ShaderResourceProvider resourceProvider;
    private ShaderConfiguration currentConfiguration;
    private Set<Identifier> lastImportedFiles = Set.of();
    
    /**
     * Create a recompilable shader with preprocessing support
     */
    public RecompilableShader(Identifier identifier, 
                             Map<ShaderType, String> shaderSources,
                             ShaderPreprocessor preprocessor,
                             ShaderResourceProvider resourceProvider) throws IOException {
        super(identifier, preprocessSources(shaderSources, identifier, preprocessor, resourceProvider));
        
        this.originalSources = Map.copyOf(shaderSources);
        this.preprocessor = preprocessor;
        this.resourceProvider = resourceProvider;
        this.currentConfiguration = ShaderConfigurationManager.getInstance().getConfiguration(identifier);
        this.lastImportedFiles = preprocessor.getLastImportedFiles();
        
        // Register for configuration changes
        ShaderConfigurationManager.getInstance().addConfigurationListener(identifier, this::onConfigurationChanged);
    }
    
    /**
     * Create a recompilable shader with single shader type
     */
    public RecompilableShader(Identifier identifier,
                             ShaderType type,
                             String source,
                             ShaderPreprocessor preprocessor,
                             ShaderResourceProvider resourceProvider) throws IOException {
        this(identifier, Map.of(type, source), preprocessor, resourceProvider);
    }
    
    /**
     * Recompile the shader with current configuration
     */
    public synchronized void recompile() throws IOException {
        try {
            // Dispose current program
            if (program > 0) {
                dispose();
            }
            
            // Get current configuration
            ShaderConfiguration config = ShaderConfigurationManager.getInstance().getConfiguration(identifier);
            
            // Preprocess sources with current configuration
            Map<ShaderType, String> processedSources = preprocessSources(
                originalSources, identifier, preprocessor, resourceProvider, config
            );
            
            // Recreate shader program
            recreateProgram(processedSources);
            
            // Update tracking
            this.currentConfiguration = new ShaderConfiguration(config);
            this.lastImportedFiles = preprocessor.getLastImportedFiles();
            
            System.out.println("Recompiled shader: " + identifier);
            
        } catch (Exception e) {
            System.err.println("Failed to recompile shader " + identifier + ": " + e.getMessage());
            throw new IOException("Shader recompilation failed", e);
        }
    }
    
    /**
     * Check if recompilation is needed due to configuration changes
     */
    public boolean needsRecompilation() {
        ShaderConfiguration currentConfig = ShaderConfigurationManager.getInstance().getConfiguration(identifier);
        return !Objects.equals(this.currentConfiguration, currentConfig);
    }
    
    /**
     * Check if any imported files have changed (requires external file monitoring)
     */
    public boolean hasImportChanges() {
        // This would need to be implemented with file system monitoring
        // For now, return false - subclasses can override
        return false;
    }
    
    /**
     * Get the set of files this shader depends on
     */
    public Set<Identifier> getDependencies() {
        return Set.copyOf(lastImportedFiles);
    }
    
    /**
     * Get current shader configuration
     */
    public ShaderConfiguration getCurrentConfiguration() {
        return new ShaderConfiguration(currentConfiguration);
    }
    
    /**
     * Force recompilation even if configuration hasn't changed
     */
    public void forceRecompile() throws IOException {
        // Temporarily modify configuration to force recompilation
        ShaderConfiguration temp = new ShaderConfiguration(currentConfiguration);
        temp.define("__FORCE_RECOMPILE__", String.valueOf(System.currentTimeMillis()));
        
        ShaderConfiguration originalConfig = currentConfiguration;
        try {
            currentConfiguration = temp;
            recompile();
        } finally {
            currentConfiguration = originalConfig;
        }
    }
    
    private void onConfigurationChanged(ShaderConfiguration newConfiguration) {
        if (!Objects.equals(this.currentConfiguration, newConfiguration)) {
            try {
                recompile();
            } catch (IOException e) {
                System.err.println("Failed to recompile shader " + identifier + " after configuration change: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private void recreateProgram(Map<ShaderType, String> processedSources) throws IOException {
        // Create new program
        int newProgram = org.lwjgl.opengl.GL20.glCreateProgram();
        
        // Store old program handle
        int oldProgram = this.program;
        
        // Replace program handle via reflection (hacky but necessary)
        try {
            var programField = Shader.class.getDeclaredField("program");
            programField.setAccessible(true);
            programField.set(this, newProgram);
        } catch (Exception e) {
            throw new IOException("Failed to update program handle", e);
        }
        
        try {
            validateShaderTypes(processedSources);
            compileAndAttachShaders(processedSources);
            linkProgram();
            cleanupShaders();
            collectAndInitializeUniforms();
            postLinkInitialization();
            
            // Delete old program
            if (oldProgram > 0) {
                org.lwjgl.opengl.GL20.glDeleteProgram(oldProgram);
            }
            
        } catch (Exception e) {
            // Restore old program on failure
            try {
                var programField = Shader.class.getDeclaredField("program");
                programField.setAccessible(true);
                programField.set(this, oldProgram);
            } catch (Exception restoreEx) {
                // Ignore restore errors
            }
            
            // Delete failed program
            if (newProgram > 0) {
                org.lwjgl.opengl.GL20.glDeleteProgram(newProgram);
            }
            
            throw e;
        }
    }
    
    @Override
    public void dispose() {
        // Unregister configuration listener
        ShaderConfigurationManager.getInstance().removeConfigurationListener(identifier, this::onConfigurationChanged);
        super.dispose();
    }
    
    /**
     * Static method to preprocess shader sources
     */
    private static Map<ShaderType, String> preprocessSources(Map<ShaderType, String> sources,
                                                            Identifier identifier,
                                                            ShaderPreprocessor preprocessor,
                                                            ShaderResourceProvider resourceProvider) throws IOException {
        ShaderConfiguration config = ShaderConfigurationManager.getInstance().getConfiguration(identifier);
        return preprocessSources(sources, identifier, preprocessor, resourceProvider, config);
    }
    
    private static Map<ShaderType, String> preprocessSources(Map<ShaderType, String> sources,
                                                            Identifier identifier,
                                                            ShaderPreprocessor preprocessor,
                                                            ShaderResourceProvider resourceProvider,
                                                            ShaderConfiguration config) throws IOException {
        preprocessor.setResourceProvider(resourceProvider);
        
        Map<ShaderType, String> processedSources = new java.util.HashMap<>();
        
        for (Map.Entry<ShaderType, String> entry : sources.entrySet()) {
            ShaderType type = entry.getKey();
            String source = entry.getValue();
            
            try {
                Identifier shaderTypeId = Identifier.of(identifier + "_" + type.name().toLowerCase());
                PreprocessorResult result = preprocessor.process(source, shaderTypeId, config.getMacros());
                
                if (result.hasWarnings()) {
                    System.out.println("Shader preprocessing warnings for " + shaderTypeId + ":\n" + result.getWarningsString());
                }
                
                processedSources.put(type, result.processedSource());
                
            } catch (ShaderPreprocessorException e) {
                throw new IOException("Failed to preprocess " + type.name().toLowerCase() + " shader: " + e.getMessage(), e);
            }
        }
        
        return processedSources;
    }
}
