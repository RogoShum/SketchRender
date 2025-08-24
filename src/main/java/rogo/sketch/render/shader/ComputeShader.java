package rogo.sketch.render.shader;

import org.lwjgl.opengl.GL43;
import rogo.sketch.api.ResourceReloadable;
import rogo.sketch.render.shader.preprocessor.ShaderPreprocessor;
import rogo.sketch.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Compute shader program for general purpose GPU computing
 * Supports automatic recompilation when configuration or dependencies change
 */
public class ComputeShader extends Shader implements ResourceReloadable<Shader> {
    
    private ReloadableShader reloadableSupport;

    /**
     * Create a compute shader from GLSL source code
     */
    public ComputeShader(Identifier identifier, String computeShaderSource) throws IOException {
        super(identifier, ShaderType.COMPUTE, computeShaderSource);
        this.reloadableSupport = null; // Non-reloadable by default
    }
    
    /**
     * Create a reloadable compute shader with preprocessing support
     */
    public ComputeShader(Identifier identifier, 
                        String computeShaderSource,
                        ShaderPreprocessor preprocessor,
                        Function<Identifier, Optional<BufferedReader>> resourceProvider) throws IOException {
        // Use parent class preprocessing constructor
        super(identifier, ShaderType.COMPUTE, computeShaderSource, preprocessor, resourceProvider);
        
        // Create reloadable support with original source
        this.reloadableSupport = new ReloadableShader(
            identifier, 
            Map.of(ShaderType.COMPUTE, computeShaderSource),
            preprocessor,
            resourceProvider
        ) {
            @Override
            protected Shader createShaderInstance(Map<ShaderType, String> processedSources) throws IOException {
                return new ComputeShader(identifier, processedSources.get(ShaderType.COMPUTE));
            }
        };
        
        // Initialize the reloadable support for future reloads
        reloadableSupport.initialize();
    }

    @Override
    protected void postLinkInitialization() {
        super.postLinkInitialization();
    }

    @Override
    protected void validateShaderTypes(Map<ShaderType, String> shaderSources) {
        if (!shaderSources.containsKey(ShaderType.COMPUTE)) {
            throw new IllegalArgumentException("Compute shader program requires a compute shader");
        }
        if (shaderSources.size() != 1) {
            throw new IllegalArgumentException("Compute shader program can only contain a compute shader");
        }
    }

    /**
     * Dispatch compute work groups
     *
     * @param numGroupsX Number of work groups in X dimension
     * @param numGroupsY Number of work groups in Y dimension
     * @param numGroupsZ Number of work groups in Z dimension
     */
    public void dispatch(int numGroupsX, int numGroupsY, int numGroupsZ) {
        GL43.glDispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
    }

    /**
     * Dispatch compute work groups (1D)
     */
    public void dispatch(int numGroups) {
        dispatch(numGroups, 1, 1);
    }

    /**
     * Dispatch compute work groups (2D)
     */
    public void dispatch(int numGroupsX, int numGroupsY) {
        dispatch(numGroupsX, numGroupsY, 1);
    }

    /**
     * Issue a memory barrier to ensure compute writes are visible
     *
     * @param barriers Bitfield of barrier types (e.g., GL_SHADER_STORAGE_BARRIER_BIT)
     */
    public void memoryBarrier(int barriers) {
        GL43.glMemoryBarrier(barriers);
    }

    /**
     * Convenience method for shader storage buffer barrier
     */
    public void shaderStorageBarrier() {
        memoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
    }

    /**
     * Convenience method for all barriers
     */
    public void allBarriers() {
        memoryBarrier(GL43.GL_ALL_BARRIER_BITS);
    }

    /**
     * Get the maximum work group count for each dimension
     */
    public static int[] getMaxWorkGroupCount() {
        int[] maxCount = new int[3];
        maxCount[0] = GL43.glGetIntegeri(GL43.GL_MAX_COMPUTE_WORK_GROUP_COUNT, 0);
        maxCount[1] = GL43.glGetIntegeri(GL43.GL_MAX_COMPUTE_WORK_GROUP_COUNT, 1);
        maxCount[2] = GL43.glGetIntegeri(GL43.GL_MAX_COMPUTE_WORK_GROUP_COUNT, 2);
        return maxCount;
    }

    /**
     * Get the maximum work group size for each dimension
     */
    public static int[] getMaxWorkGroupSize() {
        int[] maxSize = new int[3];
        maxSize[0] = GL43.glGetIntegeri(GL43.GL_MAX_COMPUTE_WORK_GROUP_SIZE, 0);
        maxSize[1] = GL43.glGetIntegeri(GL43.GL_MAX_COMPUTE_WORK_GROUP_SIZE, 1);
        maxSize[2] = GL43.glGetIntegeri(GL43.GL_MAX_COMPUTE_WORK_GROUP_SIZE, 2);
        return maxSize;
    }

    /**
     * Get the maximum total work group size
     */
    public static int getMaxWorkGroupInvocations() {
        return GL43.glGetInteger(GL43.GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS);
    }
    
    /**
     * Create a reloadable compute shader from source code
     */
    public static ComputeShader reloadable(Identifier identifier, 
                                          String computeSource,
                                          ShaderPreprocessor preprocessor,
                                          Function<Identifier, Optional<BufferedReader>> resourceProvider) throws IOException {
        return new ComputeShader(identifier, computeSource, preprocessor, resourceProvider);
    }
    
    // ResourceReloadable implementation
    
    @Override
    public boolean needsReload() {
        return reloadableSupport != null && reloadableSupport.needsReload();
    }
    
    @Override
    public void reload() throws IOException {
        if (reloadableSupport != null) {
            reloadableSupport.reload();
        }
    }
    
    @Override
    public void forceReload() throws IOException {
        if (reloadableSupport != null) {
            reloadableSupport.forceReload();
        }
    }
    
    @Override
    public Shader getCurrentResource() {
        return reloadableSupport != null ? reloadableSupport.getCurrentResource() : this;
    }
    
    @Override
    public Identifier getResourceIdentifier() {
        return identifier;
    }
    
    @Override
    public java.util.Set<Identifier> getDependencies() {
        return reloadableSupport != null ? reloadableSupport.getDependencies() : java.util.Collections.emptySet();
    }
    
    @Override
    public boolean hasDependencyChanges() {
        return reloadableSupport != null && reloadableSupport.hasDependencyChanges();
    }
    
    @Override
    public void updateDependencyTimestamps() {
        if (reloadableSupport != null) {
            reloadableSupport.updateDependencyTimestamps();
        }
    }
    
    @Override
    public void addReloadListener(java.util.function.Consumer<Shader> listener) {
        if (reloadableSupport != null) {
            reloadableSupport.addReloadListener(listener);
        }
    }
    
    @Override
    public void removeReloadListener(java.util.function.Consumer<Shader> listener) {
        if (reloadableSupport != null) {
            reloadableSupport.removeReloadListener(listener);
        }
    }
    
    @Override
    public ReloadMetadata getLastReloadMetadata() {
        return reloadableSupport != null ? reloadableSupport.getLastReloadMetadata() : null;
    }
    
    /**
     * Check if this shader supports reloading
     */
    public boolean isReloadable() {
        return reloadableSupport != null;
    }
    
    /**
     * Get the original shader sources (if reloadable)
     */
    public java.util.Map<ShaderType, String> getOriginalSources() {
        return reloadableSupport != null ? reloadableSupport.getOriginalSources() : java.util.Collections.emptyMap();
    }
    
    @Override
    public void dispose() {
        if (reloadableSupport != null) {
            reloadableSupport.dispose();
        }
        super.dispose();
    }
}