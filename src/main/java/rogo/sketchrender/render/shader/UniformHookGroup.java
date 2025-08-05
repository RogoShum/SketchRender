package rogo.sketchrender.render.shader;

import rogo.sketchrender.api.ShaderUniform;
import rogo.sketchrender.render.GraphicsInstance;
import rogo.sketchrender.render.uniform.UniformHook;

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

    public ShaderUniform<?> getUniform(final String uniformName) {
        return uniforms.get(uniformName).uniform();
    }

    public void updateUniformHooks(GraphicsInstance<?> g) {
        for (final UniformHook<?> uniformHook : uniforms.values()) {
            uniformHook.checkUpdate(g);
        }
    }
}