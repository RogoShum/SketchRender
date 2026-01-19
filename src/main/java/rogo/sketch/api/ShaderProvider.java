package rogo.sketch.api;

import rogo.sketch.render.shader.uniform.UniformHookGroup;
import rogo.sketch.util.KeyId;

import java.util.Map;

public interface ShaderProvider extends ResourceObject {
    KeyId getIdentifier();

    UniformHookGroup getUniformHookGroup();

    Map<KeyId, Map<KeyId, Integer>> getResourceBindings();
}