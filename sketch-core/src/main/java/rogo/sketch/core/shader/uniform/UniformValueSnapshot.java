package rogo.sketch.core.shader.uniform;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public class UniformValueSnapshot {
    private static final UniformValueSnapshot EMPTY = new UniformValueSnapshot(UniformLayout.empty(), new Object[0]);
    private final UniformLayout layout;
    private final Object[] values;
    private final int hashCode;

    public UniformValueSnapshot(Map<String, Object> uniformValues) {
        UniformLayout resolvedLayout = UniformLayout.from(uniformValues);
        this.layout = resolvedLayout;
        this.values = packValues(resolvedLayout, uniformValues);
        this.hashCode = Objects.hash(this.layout, Arrays.deepHashCode(this.values));
    }

    private UniformValueSnapshot(UniformLayout layout, Object[] values) {
        this.layout = layout != null ? layout : UniformLayout.empty();
        this.values = values != null ? values : new Object[0];
        this.hashCode = Objects.hash(this.layout, Arrays.deepHashCode(this.values));
    }

    public static UniformValueSnapshot empty() {
        return EMPTY;
    }

    /**
     * Capture uniform values from a hook group for the given instance.
     */
    public static UniformValueSnapshot captureFrom(UniformHookGroup hookGroup, Object instance) {
        return hookGroup.captureSnapshot(instance, null);
    }

    public static UniformValueSnapshot captureFrom(
            UniformHookGroup hookGroup,
            Object instance,
            UniformUpdateDomain domain) {
        return hookGroup.captureSnapshot(instance, domain);
    }

    /**
     * Capture uniform values using pre-cached matching hooks for better performance.
     * If cachedHooks is null, falls back to the standard capture method.
     */
    public static UniformValueSnapshot captureFrom(UniformHookGroup hookGroup, Object instance, UniformHook<?>[] cachedHooks) {
        return captureFrom(hookGroup, instance, cachedHooks, null);
    }

    public static UniformValueSnapshot captureFrom(
            UniformHookGroup hookGroup,
            Object instance,
            UniformHook<?>[] cachedHooks,
            UniformUpdateDomain domain) {
        return hookGroup.captureSnapshot(instance, cachedHooks, domain);
    }

    public void applyTo(UniformHookGroup hookGroup) {
        for (int i = 0; i < layout.size(); i++) {
            String uniformName = layout.uniformName(i);
            Object value = values[i];

            UniformHook<?> hook = hookGroup.getUniformHook(uniformName);
            if (hook != null) {
                @SuppressWarnings("unchecked")
                UniformHook<Object> typedHook = (UniformHook<Object>) hook;
                typedHook.setDirectValue(value);
            }
        }
    }

    public boolean isCompatibleWith(UniformValueSnapshot other) {
        return this.equals(other);
    }

    public boolean isEmpty() {
        return layout.isEmpty();
    }

    public java.util.Set<String> getUniformNames() {
        return layout.uniformNames();
    }

    public Object getUniformValue(String name) {
        int slot = layout.slotOf(name);
        return slot >= 0 ? values[slot] : null;
    }

    public UniformLayout layout() {
        return layout;
    }

    static UniformValueSnapshot fromSorted(String[] sortedUniformNames, Object[] sortedValues) {
        if (sortedUniformNames == null || sortedUniformNames.length == 0) {
            return EMPTY;
        }
        return new UniformValueSnapshot(UniformLayout.fromSorted(sortedUniformNames), sortedValues);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        UniformValueSnapshot that = (UniformValueSnapshot) obj;
        return Objects.equals(layout, that.layout) && Arrays.deepEquals(values, that.values);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "UniformSnapshot{" + layout.uniformNames() + "}";
    }

    private static Object[] packValues(UniformLayout layout, Map<String, Object> uniformValues) {
        if (layout == null || layout.isEmpty() || uniformValues == null || uniformValues.isEmpty()) {
            return new Object[0];
        }
        Object[] packed = new Object[layout.size()];
        for (int i = 0; i < layout.size(); i++) {
            packed[i] = uniformValues.get(layout.uniformName(i));
        }
        return packed;
    }
}
