package rogo.sketch.core.shader.uniform;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.ShaderResource;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;
import java.util.Set;

public class UniformHook<T> {
    private final KeyId keyId;
    private final ShaderResource<T> uniform;
    private final ValueGetter<T> valueGetter;
    private T value;

    public UniformHook(KeyId keyId, ShaderResource<T> uniform, ValueGetter<T> valueGetter) {
        this.keyId = keyId;
        this.uniform = uniform;
        this.valueGetter = valueGetter;
    }

    public void checkUpdate(Object graph) {
        T currentValue = valueGetter.get(graph);
        if (currentValue != null && !Objects.equals(currentValue, value)) {
            value = currentValue;
            uniform.set(value);
        }
    }


    @Nullable
    public T getDirectValue(Object graph) {
        return valueGetter.get(graph);
    }

    public void setDirectValue(T newValue) {
        if (newValue != null && !Objects.equals(newValue, value)) {
            value = newValue;
            uniform.set(value);
        }
    }

    public T getCurrentValue() {
        return value;
    }

    public KeyId getIdentifier() {
        return keyId;
    }

    public ShaderResource<T> uniform() {
        return uniform;
    }

    /**
     * Get the target classes this UniformHook can work with
     */
    public Set<Class<?>> getTargetClasses() {
        return valueGetter.getTargetClasses();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UniformHook<?> that = (UniformHook<?>) o;
        return Objects.equals(keyId, that.keyId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(keyId);
    }
}