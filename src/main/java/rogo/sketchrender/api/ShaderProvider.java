package rogo.sketchrender.api;

import rogo.sketchrender.render.sketch.shader.UniformHookGroup;
import rogo.sketchrender.util.Identifier;

public interface ShaderProvider {
    Identifier getIdentifier();

    UniformHookGroup getUniformHookGroup();

    int getHandle();
}