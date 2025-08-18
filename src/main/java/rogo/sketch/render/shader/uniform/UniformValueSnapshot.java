package rogo.sketch.render.shader.uniform;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class UniformValueSnapshot {
    private final Map<String, Object> uniformValues;
    private final int hashCode;

    public UniformValueSnapshot(Map<String, Object> uniformValues) {
        this.uniformValues = Map.copyOf(uniformValues);
        this.hashCode = Objects.hash(this.uniformValues);
    }

    public static UniformValueSnapshot empty() {
        return new UniformValueSnapshot(Map.of());
    }

    public static UniformValueSnapshot captureFrom(UniformHookGroup hookGroup, Object instance) {
        Map<String, Object> values = hookGroup.getUniformsDirect(instance);
        return new UniformValueSnapshot(values);
    }

    public void applyTo(UniformHookGroup hookGroup) {
        for (Map.Entry<String, Object> entry : uniformValues.entrySet()) {
            String uniformName = entry.getKey();
            Object value = entry.getValue();

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
        return uniformValues.isEmpty();
    }

    public java.util.Set<String> getUniformNames() {
        return uniformValues.keySet();
    }

    public Object getUniformValue(String name) {
        return uniformValues.get(name);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        UniformValueSnapshot that = (UniformValueSnapshot) obj;
        return Objects.equals(uniformValues, that.uniformValues);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "UniformSnapshot{" + uniformValues + "}";
    }
}
