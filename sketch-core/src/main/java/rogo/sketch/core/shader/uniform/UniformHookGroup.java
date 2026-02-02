package rogo.sketch.core.shader.uniform;

import rogo.sketch.core.api.ShaderResource;

import java.util.*;

public class UniformHookGroup {
    private final Map<String, UniformHook<?>> uniforms = new HashMap<>();
    private final Map<Class<?>, List<UniformHook<?>>> classToHooksMap = new HashMap<>();
    private final List<UniformHook<?>> universalHooks = new ArrayList<>();
    private final Map<UniformHook<?>, String> hookToNameMap = new HashMap<>();
    private final Map<Class<?>, List<UniformHook<?>>> matchingHooksCache = new HashMap<>();

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

        matchingHooksCache.clear();
    }

    public UniformHook<?> getUniformHook(final String uniformName) {
        return uniforms.get(uniformName);
    }

    public ShaderResource<?> getUniform(final String uniformName) {
        return uniforms.get(uniformName).uniform();
    }

    public void updateUniforms(Object c) {
        for (int i = 0; i < universalHooks.size(); i++) {
            universalHooks.get(i).checkUpdate(c);
        }

        Class<?> objectClass = c.getClass();
        List<UniformHook<?>> matchingHooks = getMatchingHooks(objectClass);
        for (int i = 0; i < matchingHooks.size(); i++) {
            matchingHooks.get(i).checkUpdate(c);
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
        for (int i = 0; i < matchingHooks.size(); i++) {
            UniformHook<?> uniformHook = matchingHooks.get(i);
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
     * Get uniform values using pre-cached matching hooks.
     * This avoids the overhead of repeatedly computing matching hooks for the same class.
     * 
     * @param c The object to get values from
     * @param cachedMatchingHooks Pre-computed matching hooks (from getAllMatchingHooks)
     * @return Map of uniform name to value
     */
    public Map<String, Object> getUniformsDirectWithCachedHooks(Object c, List<UniformHook<?>> cachedMatchingHooks) {
        Map<String, Object> values = new HashMap<>();

        // Universal hooks are always applied
        for (int i = 0; i < universalHooks.size(); i++) {
            UniformHook<?> uniformHook = universalHooks.get(i);
            Object currentValue = uniformHook.getDirectValue(c);
            if (currentValue != null) {
                String uniformName = hookToNameMap.get(uniformHook);
                if (uniformName != null) {
                    values.put(uniformName, currentValue);
                }
            }
        }

        // Use cached hooks instead of computing matching hooks
        for (int i = 0; i < cachedMatchingHooks.size(); i++) {
            UniformHook<?> uniformHook = cachedMatchingHooks.get(i);
            Object currentValue = uniformHook.getDirectValue(c);
            if (currentValue != null) {
                String uniformName = hookToNameMap.get(uniformHook);
                if (uniformName != null) {
                    values.put(uniformName, currentValue);
                }
            }
        }

        return values;
    }
    
    /**
     * Get all matching hooks for a given object class.
     * Use this to pre-compute the hooks for a class and pass to getUniformsDirectWithCachedHooks.
     * 
     * @param objectClass The class to get matching hooks for
     * @return List of matching uniform hooks
     */
    public List<UniformHook<?>> getAllMatchingHooks(Class<?> objectClass) {
        return getMatchingHooks(objectClass);
    }
    
    /**
     * Get the universal hooks list for caching purposes.
     */
    public List<UniformHook<?>> getUniversalHooks() {
        return Collections.unmodifiableList(universalHooks);
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
        List<UniformHook<?>> cachedResult = matchingHooksCache.get(objectClass);
        if (cachedResult != null) {
            return cachedResult;
        }

        List<UniformHook<?>> result = new ArrayList<>();
        for (Map.Entry<Class<?>, List<UniformHook<?>>> entry : classToHooksMap.entrySet()) {
            Class<?> targetClass = entry.getKey();
            if (targetClass.isAssignableFrom(objectClass)) {
                result.addAll(entry.getValue());
            }
        }

        if (result.isEmpty()) {
            result = Collections.emptyList();
        } else {
            ((ArrayList<?>) result).trimToSize();
        }

        matchingHooksCache.put(objectClass, result);
        return result;
    }

    public Set<String> getUniformNames() {
        return uniforms.keySet();
    }

    public boolean hasUniform(String uniformName) {
        return uniforms.containsKey(uniformName);
    }
}