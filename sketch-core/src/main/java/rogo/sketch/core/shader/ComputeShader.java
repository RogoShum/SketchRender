package rogo.sketch.core.shader;

import org.lwjgl.opengl.GL43;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.shader.preprocessor.ShaderPreprocessor;
import rogo.sketch.core.shader.vertex.ShaderVertexLayout;
import rogo.sketch.core.util.KeyId;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Compute shader program for general purpose GPU computing.
 * <p>
 * Note: For reloadable/recompilable shaders, use {@link rogo.sketch.core.shader.variant.ShaderTemplate}
 * instead. ShaderTemplate handles macro variants and automatic recompilation via MacroContext.
 */
public class ComputeShader extends Shader {
    /**
     * Create a compute shader with preprocessing support and macros
     */
    public ComputeShader(KeyId keyId,
                         String computeShaderSource,
                         ShaderPreprocessor preprocessor,
                         Function<KeyId, Optional<InputStream>> resourceProvider,
                         Map<String, String> macros,
                         ShaderVertexLayout shaderVertexLayout) throws IOException {
        super(keyId, ShaderType.COMPUTE, computeShaderSource, preprocessor, resourceProvider, macros, shaderVertexLayout);
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
        GraphicsDriver.getCurrentAPI().dispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
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
        GraphicsDriver.getCurrentAPI().memoryBarrier(barriers);
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
}