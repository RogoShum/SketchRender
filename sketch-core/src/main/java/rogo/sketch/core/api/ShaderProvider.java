package rogo.sketch.core.api;

import rogo.sketch.core.shader.uniform.UniformHookGroup;
import rogo.sketch.core.util.KeyId;

import java.util.Map;

public interface ShaderProvider extends ResourceObject {
    KeyId getIdentifier();

    UniformHookGroup getUniformHookGroup();

    Map<KeyId, Map<KeyId, Integer>> getResourceBindings();
}