package rogo.sketch.render.shader;

import org.lwjgl.opengl.GL43;

import java.io.IOException;
import java.util.Map;

/**
 * Compute shader program for general purpose GPU computing
 */
public class ComputeShaderProgram extends Shader {

    /**
     * Create a compute shader from GLSL source code
     */
    public ComputeShaderProgram(String identifier, String computeShaderSource) throws IOException {
        super(identifier, ShaderType.COMPUTE, computeShaderSource);
    }

    @Override
    protected void postLinkInitialization() {
        // No additional initialization needed for compute shaders
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
        bind();
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
     * Create a compute shader from source code
     */
    public static ComputeShaderProgram fromSource(String identifier, String computeSource) throws IOException {
        return new ComputeShaderProgram(identifier, computeSource);
    }
}