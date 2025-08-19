package rogo.sketch.render.shader.uniform;

import rogo.sketch.api.ShaderResource;

import java.util.*;

public class UniformHookGroup {
    private final Map<String, UniformHook<?>> uniforms = new HashMap<>();
    private final Map<Class<?>, List<UniformHook<?>>> classToHooksMap = new HashMap<>();
    private final List<UniformHook<?>> universalHooks = new ArrayList<>();
    private final Map<UniformHook<?>, String> hookToNameMap = new HashMap<>();

    public UniformHookGroup() {
    }

    public void addUniform(final String uniformName, final UniformHook<?> uniform) {
        uniforms.put(uniformName, uniform);
        hookToNameMap.put(uniform, uniformName);

        Set<Class<?>> targetClasses = uniform.getTargetClasses();
        if (targetClasses.isEmpty()) {
            universalHooks.add(uniform);
        } else {
            for (Class<?> targetClass : targetClasses) {
                classToHooksMap.computeIfAbsent(targetClass, k -> new ArrayList<>()).add(uniform);
            }
        }
    }

    public UniformHook<?> getUniformHook(final String uniformName) {
        return uniforms.get(uniformName);
    }

    public ShaderResource<?> getUniform(final String uniformName) {
        return uniforms.get(uniformName).uniform();
    }

    public void updateUniforms(Object c) {
        for (UniformHook<?> uniformHook : universalHooks) {
            uniformHook.checkUpdate(c);
        }

        Class<?> objectClass = c.getClass();
        List<UniformHook<?>> matchingHooks = getMatchingHooks(objectClass);
        for (UniformHook<?> uniformHook : matchingHooks) {
            uniformHook.checkUpdate(c);
        }
    }

    public Map<String, Object> getUniformsDirect(Object c) {
        Map<String, Object> values = new HashMap<>();

        for (UniformHook<?> uniformHook : universalHooks) {
            Object currentValue = uniformHook.getDirectValue(c);
            if (currentValue != null) {
                String uniformName = getUniformName(uniformHook);
                if (uniformName != null) {
                    values.put(uniformName, currentValue);
                }
            }
        }

        Class<?> objectClass = c.getClass();
        List<UniformHook<?>> matchingHooks = getMatchingHooks(objectClass);
        for (UniformHook<?> uniformHook : matchingHooks) {
            Object currentValue = uniformHook.getDirectValue(c);
            if (currentValue != null) {
                String uniformName = getUniformName(uniformHook);
                if (uniformName != null) {
                    values.put(uniformName, currentValue);
                }
            }
        }

        return values;
    }

    /**
     * Get the uniform name for a given UniformHook
     */
    private String getUniformName(UniformHook<?> hook) {
        return hookToNameMap.get(hook);
    }

    /**
     * Get all UniformHooks that can handle the given object class
     * This includes exact matches and superclass/interface matches
     */
    private List<UniformHook<?>> getMatchingHooks(Class<?> objectClass) {
        List<UniformHook<?>> result = new ArrayList<>();

        for (Map.Entry<Class<?>, List<UniformHook<?>>> entry : classToHooksMap.entrySet()) {
            Class<?> targetClass = entry.getKey();
            if (targetClass.isAssignableFrom(objectClass)) {
                result.addAll(entry.getValue());
            }
        }

        return result;
    }

    public Set<String> getUniformNames() {
        return uniforms.keySet();
    }

    public boolean hasUniform(String uniformName) {
        return uniforms.containsKey(uniformName);
    }
}