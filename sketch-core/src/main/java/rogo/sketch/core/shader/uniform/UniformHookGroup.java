package rogo.sketch.core.shader.uniform;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import rogo.sketch.core.api.ShaderResource;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class UniformHookGroup {
    private static final UniformHook<?>[] EMPTY_HOOKS = new UniformHook<?>[0];

    private final Object2ObjectOpenHashMap<String, UniformHook<?>> uniforms = new Object2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<Class<?>, ObjectArrayList<UniformHook<?>>> classToHooksMap = new Reference2ObjectOpenHashMap<>();
    private final ObjectArrayList<UniformHook<?>> universalHookList = new ObjectArrayList<>();
    private final Reference2ObjectOpenHashMap<UniformHook<?>, String> hookToNameMap = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<Class<?>, UniformHook<?>[]> allMatchingHooksCache = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<Class<?>, UniformHook<?>[]> buildSnapshotHooksCache = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<Class<?>, UniformHook<?>[]> frameLiveHooksCache = new Reference2ObjectOpenHashMap<>();
    private UniformHook<?>[] universalHooks = EMPTY_HOOKS;
    private UniformHook<?>[] buildSnapshotUniversalHooks = EMPTY_HOOKS;
    private UniformHook<?>[] frameLiveUniversalHooks = EMPTY_HOOKS;

    public UniformHookGroup() {
    }

    public void addUniform(final String uniformName, final UniformHook<?> uniform) {
        uniforms.put(uniformName, uniform);
        hookToNameMap.put(uniform, uniformName);

        Set<Class<?>> targetClasses = uniform.getTargetClasses();
        if (targetClasses.isEmpty()) {
            universalHookList.add(uniform);
        } else {
            for (Class<?> targetClass : targetClasses) {
                classToHooksMap.computeIfAbsent(targetClass, k -> new ObjectArrayList<>()).add(uniform);
            }
        }

        refreshUniversalCaches();
        allMatchingHooksCache.clear();
        buildSnapshotHooksCache.clear();
        frameLiveHooksCache.clear();
    }

    public UniformHook<?> getUniformHook(final String uniformName) {
        return uniforms.get(uniformName);
    }

    @SuppressWarnings("unchecked")
    public <T> ShaderResource<T> getUniform(final String uniformName) {
        UniformHook<?> hook = uniforms.get(uniformName);
        if (hook == null) {
            throw new IllegalArgumentException(
                    "Missing shader uniform hook '" + uniformName + "'. Registered uniforms: " + uniforms.keySet());
        }
        return (ShaderResource<T>) hook.uniform();
    }

    public void updateUniforms(Object c) {
        updateUniforms(c, null);
    }

    public void updateUniforms(Object c, UniformUpdateDomain domain) {
        UniformHook<?>[] hooks = selectUniversalHooks(domain);
        for (int i = 0; i < hooks.length; i++) {
            hooks[i].checkUpdate(c);
        }

        UniformHook<?>[] matchingHooks = getMatchingHooks(c.getClass(), domain);
        for (int i = 0; i < matchingHooks.length; i++) {
            matchingHooks[i].checkUpdate(c);
        }
    }

    public Map<String, Object> getUniformsDirect(Object c) {
        return getUniformsDirect(c, null);
    }

    public Map<String, Object> getUniformsDirect(Object c, UniformUpdateDomain domain) {
        return captureValues(c, getMatchingHooks(c.getClass(), domain), domain).toMap();
    }

    /**
     * Get uniform values using pre-cached matching hooks.
     * This avoids the overhead of repeatedly computing matching hooks for the same class.
     *
     * @param c                   The object to get values from
     * @param cachedMatchingHooks Pre-computed matching hooks (from getAllMatchingHooks)
     * @return Map of uniform name to value
     */
    public Map<String, Object> getUniformsDirectWithCachedHooks(Object c, UniformHook<?>[] cachedMatchingHooks) {
        return getUniformsDirectWithCachedHooks(c, cachedMatchingHooks, null);
    }

    public Map<String, Object> getUniformsDirectWithCachedHooks(Object c, UniformHook<?>[] cachedMatchingHooks, UniformUpdateDomain domain) {
        return captureValues(c, cachedMatchingHooks, domain).toMap();
    }

    UniformValueSnapshot captureSnapshot(Object source, UniformUpdateDomain domain) {
        if (source == null) {
            return UniformValueSnapshot.empty();
        }
        return captureValues(source, getMatchingHooks(source.getClass(), domain), domain).snapshot();
    }

    UniformValueSnapshot captureSnapshot(
            Object source,
            UniformHook<?>[] cachedMatchingHooks,
            UniformUpdateDomain domain) {
        if (source == null) {
            return UniformValueSnapshot.empty();
        }
        UniformHook<?>[] hooks = cachedMatchingHooks != null
                ? cachedMatchingHooks
                : getMatchingHooks(source.getClass(), domain);
        return captureValues(source, hooks, domain).snapshot();
    }

    /**
     * Get all matching hooks for a given object class.
     * Use this to pre-compute the hooks for a class and pass to getUniformsDirectWithCachedHooks.
     *
     * @param objectClass The class to get matching hooks for
     * @return List of matching uniform hooks
     */
    public UniformHook<?>[] getAllMatchingHooks(Class<?> objectClass) {
        return getMatchingHooks(objectClass, null);
    }

    /**
     * Get the universal hooks list for caching purposes.
     */
    public UniformHook<?>[] getUniversalHooks() {
        return Arrays.copyOf(universalHooks, universalHooks.length);
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
    private UniformHook<?>[] getMatchingHooks(Class<?> objectClass, UniformUpdateDomain domain) {
        Reference2ObjectOpenHashMap<Class<?>, UniformHook<?>[]> cache = selectMatchingHooksCache(domain);
        UniformHook<?>[] cachedResult = cache.get(objectClass);
        if (cachedResult != null) {
            return cachedResult;
        }

        ObjectArrayList<UniformHook<?>> result = new ObjectArrayList<>();
        for (Map.Entry<Class<?>, ObjectArrayList<UniformHook<?>>> entry : classToHooksMap.reference2ObjectEntrySet()) {
            Class<?> targetClass = entry.getKey();
            if (targetClass.isAssignableFrom(objectClass)) {
                ObjectArrayList<UniformHook<?>> hooks = entry.getValue();
                for (int i = 0; i < hooks.size(); i++) {
                    UniformHook<?> hook = hooks.get(i);
                    if (matchesDomain(hook, domain)) {
                        result.add(hook);
                    }
                }
            }
        }

        UniformHook<?>[] resolved = result.isEmpty() ? EMPTY_HOOKS : result.toArray(new UniformHook<?>[0]);
        cache.put(objectClass, resolved);
        return resolved;
    }

    public Set<String> getUniformNames() {
        return Collections.unmodifiableSet(new ObjectOpenHashSet<>(uniforms.keySet()));
    }

    public boolean hasUniform(String uniformName) {
        return uniforms.containsKey(uniformName);
    }

    private boolean matchesDomain(UniformHook<?> hook, UniformUpdateDomain domain) {
        return domain == null || hook.domain() == domain;
    }

    private void refreshUniversalCaches() {
        universalHooks = universalHookList.isEmpty() ? EMPTY_HOOKS : universalHookList.toArray(new UniformHook<?>[0]);
        buildSnapshotUniversalHooks = filterByDomain(universalHooks, UniformUpdateDomain.BUILD_SNAPSHOT);
        frameLiveUniversalHooks = filterByDomain(universalHooks, UniformUpdateDomain.FRAME_LIVE);
    }

    private UniformHook<?>[] selectUniversalHooks(UniformUpdateDomain domain) {
        if (domain == UniformUpdateDomain.BUILD_SNAPSHOT) {
            return buildSnapshotUniversalHooks;
        }
        if (domain == UniformUpdateDomain.FRAME_LIVE) {
            return frameLiveUniversalHooks;
        }
        return universalHooks;
    }

    private Reference2ObjectOpenHashMap<Class<?>, UniformHook<?>[]> selectMatchingHooksCache(UniformUpdateDomain domain) {
        if (domain == UniformUpdateDomain.BUILD_SNAPSHOT) {
            return buildSnapshotHooksCache;
        }
        if (domain == UniformUpdateDomain.FRAME_LIVE) {
            return frameLiveHooksCache;
        }
        return allMatchingHooksCache;
    }

    private UniformHook<?>[] filterByDomain(UniformHook<?>[] hooks, UniformUpdateDomain domain) {
        if (hooks.length == 0) {
            return EMPTY_HOOKS;
        }
        ObjectArrayList<UniformHook<?>> filtered = new ObjectArrayList<>(hooks.length);
        for (UniformHook<?> hook : hooks) {
            if (matchesDomain(hook, domain)) {
                filtered.add(hook);
            }
        }
        return filtered.isEmpty() ? EMPTY_HOOKS : filtered.toArray(new UniformHook<?>[0]);
    }

    private UniformCaptureBuffer captureValues(
            Object source,
            UniformHook<?>[] matchingHooks,
            UniformUpdateDomain domain) {
        UniformCaptureBuffer buffer = UniformCaptureBuffer.acquire();
        appendDirectValues(source, domain, matchingHooks, buffer);
        return buffer;
    }

    private void appendDirectValues(
            Object source,
            UniformUpdateDomain domain,
            UniformHook<?>[] matchingHooks,
            UniformCaptureBuffer sink) {
        UniformHook<?>[] universal = selectUniversalHooks(domain);
        for (int i = 0; i < universal.length; i++) {
            appendValue(source, universal[i], sink);
        }
        if (matchingHooks == null) {
            return;
        }
        for (int i = 0; i < matchingHooks.length; i++) {
            appendValue(source, matchingHooks[i], sink);
        }
    }

    private void appendValue(
            Object source,
            UniformHook<?> hook,
            UniformCaptureBuffer sink) {
        Object currentValue = hook.getDirectValue(source);
        if (currentValue == null) {
            return;
        }
        String uniformName = hookToNameMap.get(hook);
        if (uniformName != null) {
            sink.put(uniformName, currentValue);
        }
    }
}
