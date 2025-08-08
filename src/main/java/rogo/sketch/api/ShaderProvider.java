package rogo.sketch.api;

import rogo.sketch.render.shader.UniformHookGroup;
import rogo.sketch.util.Identifier;

public interface ShaderProvider {
    Identifier getIdentifier();

    UniformHookGroup getUniformHookGroup();

    int getHandle();
}