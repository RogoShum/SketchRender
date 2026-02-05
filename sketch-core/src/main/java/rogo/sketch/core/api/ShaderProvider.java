package rogo.sketch.core.api;

import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.util.KeyId;

import java.util.Map;

/**
 * Interface for shader program providers.
 * Extends GpuObject since shaders have native program handles.
 */
public interface ShaderProvider extends GpuObject {
    KeyId getIdentifier();

    UniformHookGroup getUniformHookGroup();

    Map<KeyId, Map<KeyId, Integer>> getResourceBindings();
}