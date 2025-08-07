package rogo.sketchrender.render.sketch.shader;

import rogo.sketchrender.api.ShaderResource;
import rogo.sketchrender.render.sketch.uniform.UniformHook;

import java.util.HashMap;
import java.util.Map;

public class UniformHookGroup {
    private final Map<String, UniformHook<?>> uniforms = new HashMap<>();

    public UniformHookGroup() {
    }

    public void addUniform(final String uniformName, final UniformHook<?> uniform) {
        uniforms.put(uniformName, uniform);
    }

    public UniformHook<?> getUniformHook(final String uniformName) {
        return uniforms.get(uniformName);
    }

    public ShaderResource<?> getUniform(final String uniformName) {
        return uniforms.get(uniformName).uniform();
    }

    public void updateUniforms(Object c) {
        for (final UniformHook<?> uniformHook : uniforms.values()) {
            uniformHook.checkUpdate(c);
        }
    }
}