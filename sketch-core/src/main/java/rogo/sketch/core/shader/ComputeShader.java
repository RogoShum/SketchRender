package rogo.sketch.core.shader;
import rogo.sketch.core.api.ShaderProvider;

/**
 * Backend-neutral compute program adapter.
 * Live OpenGL/Vulkan program implementations own the actual dispatch behavior.
 */
public interface ComputeShader extends ShaderProvider {
    int BARRIER_SHADER_STORAGE = 1 << 0;
    int BARRIER_ALL = -1;

    void dispatch(int numGroupsX, int numGroupsY, int numGroupsZ);

    default void dispatch(int numGroups) {
        dispatch(numGroups, 1, 1);
    }

    default void dispatch(int numGroupsX, int numGroupsY) {
        dispatch(numGroupsX, numGroupsY, 1);
    }

    void memoryBarrier(int barriers);

    default void shaderStorageBarrier() {
        memoryBarrier(BARRIER_SHADER_STORAGE);
    }

    default void allBarriers() {
        memoryBarrier(BARRIER_ALL);
    }
}

