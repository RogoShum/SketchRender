package rogo.sketchrender.api;

import rogo.sketchrender.render.sketch.shader.UniformHookGroup;

public interface ShaderProvider {
    String getIdentifier();

    UniformHookGroup getUniformHookGroup();

    int getHandle();
}