package rogo.sketchrender.render.uniform;

import rogo.sketchrender.api.ShaderUniform;
import rogo.sketchrender.render.GraphicsInstance;
import rogo.sketchrender.util.Identifier;

import java.util.Objects;

public class UniformHook<T> {
    private final Identifier identifier;
    private final ShaderUniform<T> uniform;
    private final ValueGetter<T> valueGetter;
    private T value;

    public UniformHook(Identifier identifier, ShaderUniform<T> uniform, ValueGetter<T> valueGetter) {
        this.identifier = identifier;
        this.uniform = uniform;
        this.valueGetter = valueGetter;
    }

    public void checkUpdate(GraphicsInstance<?> graph) {
        T currentValue = valueGetter.get(graph, identifier);
        if (!currentValue.equals(value)) {
            value = currentValue;
            uniform.set(value);
        }
    }

    public ShaderUniform<T> uniform() {
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