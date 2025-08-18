package rogo.sketch.render.shader.uniform;

import rogo.sketch.api.ShaderResource;
import rogo.sketch.util.Identifier;

import java.util.Objects;

public class UniformHook<T> {
    private final Identifier identifier;
    private final ShaderResource<T> uniform;
    private final ValueGetter<T> valueGetter;
    private T value;

    public UniformHook(Identifier identifier, ShaderResource<T> uniform, ValueGetter<T> valueGetter) {
        this.identifier = identifier;
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

    public T getDirectValue(Object graph) {
        T currentValue = valueGetter.get(graph);
        return currentValue == null ? value : currentValue;
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

    public Identifier getIdentifier() {
        return identifier;
    }

    public ShaderResource<T> uniform() {
        return uniform;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UniformHook<?> that = (UniformHook<?>) o;
        return Objects.equals(identifier, that.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(identifier);
    }
}