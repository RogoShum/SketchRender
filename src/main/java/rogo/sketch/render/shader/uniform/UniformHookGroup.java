package rogo.sketch.render.shader.uniform;

import rogo.sketch.api.ShaderResource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    public Map<String, Object> getUniformsDirect(Object c) {
        Map<String, Object> values = new HashMap<>();
        for (Map.Entry<String, UniformHook<?>> entry : uniforms.entrySet()) {
            Object currentValue = entry.getValue().getDirectValue(c);
            if (currentValue != null) {
                values.put(entry.getKey(), currentValue);
            }
        }

        return values;
    }

    public Set<String> getUniformNames() {
        return uniforms.keySet();
    }

    public boolean hasUniform(String uniformName) {
        return uniforms.containsKey(uniformName);
    }
}