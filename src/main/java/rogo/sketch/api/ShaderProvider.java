package rogo.sketch.api;

import rogo.sketch.render.uniform.UniformHookGroup;
import rogo.sketch.util.Identifier;

import java.util.Map;

public interface ShaderProvider extends ResourceObject {
    Identifier getIdentifier();

    UniformHookGroup getUniformHookGroup();

    Map<Identifier, Map<Identifier, Integer>> getResourceBindings();
}