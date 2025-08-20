package rogo.sketch.render.shader.migration;

import net.minecraft.server.packs.resources.ResourceProvider;
import rogo.sketch.render.shader.*;
import rogo.sketch.render.shader.config.ShaderConfiguration;
import rogo.sketch.render.shader.preprocessor.*;
import rogo.sketch.util.Identifier;

import java.io.IOException;
import java.util.Map;

/**
 * Utility class to help migrate from traditional shaders to recompilable shaders
 */
public class ShaderMigrationUtils {
    
    /**
     * Convert a traditional GraphicsShader to RecompilableGraphicsShader
     */
    public static RecompilableGraphicsShader convertToRecompilable(
            GraphicsShader originalShader,
            Map<ShaderType, String> originalSources,
            ResourceProvider resourceProvider) throws IOException {
        
        ShaderPreprocessor preprocessor = new ModernShaderPreprocessor();
        ShaderResourceProvider shaderResourceProvider = new MinecraftShaderResourceProvider(resourceProvider);
        
        return new RecompilableGraphicsShader(
            originalShader.getIdentifier(),
            originalSources,
            preprocessor,
            shaderResourceProvider
        );
    }
    
    /**
     * Convert a traditional ComputeShader to RecompilableComputeShader
     */
    public static RecompilableComputeShader convertToRecompilable(
            ComputeShader originalShader,
            String originalSource,
            ResourceProvider resourceProvider) throws IOException {
        
        ShaderPreprocessor preprocessor = new ModernShaderPreprocessor();
        ShaderResourceProvider shaderResourceProvider = new MinecraftShaderResourceProvider(resourceProvider);
        
        return new RecompilableComputeShader(
            originalShader.getIdentifier(),
            originalSource,
            preprocessor,
            shaderResourceProvider
        );
    }
    
    /**
     * Create a legacy compatibility wrapper that provides the old interface
     * but uses the new recompilable system underneath
     */
    public static LegacyShaderWrapper createLegacyWrapper(
            Identifier identifier,
            Map<ShaderType, String> sources,
            ResourceProvider resourceProvider) throws IOException {
        
        RecompilableGraphicsShader recompilableShader = new RecompilableGraphicsShader(
            identifier,
            sources,
            new ModernShaderPreprocessor(),
            new MinecraftShaderResourceProvider(resourceProvider)
        );
        
        return new LegacyShaderWrapper(recompilableShader);
    }
    
    /**
     * Extract shader sources from existing shader for migration
     * Note: This is a helper method - in practice, you'd need to store
     * the original sources when creating shaders
     */
    public static void suggestMigrationStrategy(Shader existingShader) {
        System.out.println("=== Shader Migration Strategy for " + existingShader.getIdentifier() + " ===");
        System.out.println("1. Store original shader sources when creating shaders");
        System.out.println("2. Use ShaderFactory instead of direct Shader constructors");
        System.out.println("3. Configure shader settings using ShaderConfiguration");
        System.out.println("4. Replace manual recompilation with automatic recompilation");
        System.out.println("5. Use AdvancedShaderManager for centralized management");
        
        System.out.println("\nExample migration code:");
        System.out.println("// Old way:");
        System.out.println("GraphicsShader shader = new GraphicsShader(id, sources);");
        System.out.println("");
        System.out.println("// New way:");
        System.out.println("AdvancedShaderManager manager = AdvancedShaderManager.getInstance();");
        System.out.println("manager.setShaderConfiguration(id, config);");
        System.out.println("RecompilableGraphicsShader shader = manager.createGraphicsShader(id, sources);");
    }
    
    /**
     * Legacy wrapper that provides the old Shader interface while using
     * the new recompilable system underneath
     */
    public static class LegacyShaderWrapper extends GraphicsShader {
        private final RecompilableGraphicsShader underlyingShader;
        
        public LegacyShaderWrapper(RecompilableGraphicsShader underlyingShader) throws IOException {
            super(underlyingShader.getIdentifier(), Map.of(
                ShaderType.VERTEX, "// Placeholder - actual implementation delegated",
                ShaderType.FRAGMENT, "// Placeholder - actual implementation delegated"
            ));
            this.underlyingShader = underlyingShader;
        }
        
        @Override
        public void bind() {
            underlyingShader.bind();
        }
        
        @Override
        public int getHandle() {
            return underlyingShader.getHandle();
        }
        
        @Override
        public Identifier getIdentifier() {
            return underlyingShader.getIdentifier();
        }
        
        @Override
        public void dispose() {
            underlyingShader.dispose();
        }
        
        @Override
        public boolean isDisposed() {
            return underlyingShader.isDisposed();
        }
        
        // Additional methods specific to the recompilable shader
        public void recompile() throws IOException {
            underlyingShader.recompile();
        }
        
        public boolean needsRecompilation() {
            return underlyingShader.needsRecompilation();
        }
        
        public ShaderConfiguration getCurrentConfiguration() {
            return underlyingShader.getCurrentConfiguration();
        }
    }
    
    /**
     * Batch migration utility for converting multiple shaders
     */
    public static class BatchMigration {
        private final ResourceProvider resourceProvider;
        private final AdvancedShaderManager manager;
        
        public BatchMigration(ResourceProvider resourceProvider) {
            this.resourceProvider = resourceProvider;
            AdvancedShaderManager.initialize(resourceProvider);
            this.manager = AdvancedShaderManager.getInstance();
        }
        
        /**
         * Migrate a batch of shaders with their configurations
         */
        public void migrateShaders(Map<Identifier, ShaderMigrationInfo> shaderInfo) {
            for (Map.Entry<Identifier, ShaderMigrationInfo> entry : shaderInfo.entrySet()) {
                Identifier shaderId = entry.getKey();
                ShaderMigrationInfo info = entry.getValue();
                
                try {
                    // Set configuration
                    if (info.configuration != null) {
                        manager.setShaderConfiguration(shaderId, info.configuration);
                    }
                    
                    // Create shader
                    if (info.isComputeShader) {
                        manager.createComputeShader(shaderId, info.computeSource);
                    } else {
                        manager.createGraphicsShader(shaderId, info.shaderSources);
                    }
                    
                    System.out.println("Successfully migrated shader: " + shaderId);
                    
                } catch (IOException e) {
                    System.err.println("Failed to migrate shader " + shaderId + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Information needed for shader migration
     */
    public static class ShaderMigrationInfo {
        public final boolean isComputeShader;
        public final Map<ShaderType, String> shaderSources;
        public final String computeSource;
        public final ShaderConfiguration configuration;
        
        // For graphics shaders
        public ShaderMigrationInfo(Map<ShaderType, String> shaderSources, ShaderConfiguration configuration) {
            this.isComputeShader = false;
            this.shaderSources = shaderSources;
            this.computeSource = null;
            this.configuration = configuration;
        }
        
        // For compute shaders
        public ShaderMigrationInfo(String computeSource, ShaderConfiguration configuration) {
            this.isComputeShader = true;
            this.shaderSources = null;
            this.computeSource = computeSource;
            this.configuration = configuration;
        }
    }
}
