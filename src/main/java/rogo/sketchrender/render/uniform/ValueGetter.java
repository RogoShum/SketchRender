package rogo.sketchrender.render.uniform;

import org.joml.*;
import rogo.sketchrender.render.GraphicsInstance;
import rogo.sketchrender.util.Identifier;

import java.util.Set;
import java.util.function.BiFunction;

public class ValueGetter<T> {
    private final BiFunction<GraphicsInstance<?>, Identifier, T> valueGetter;

    private static final Set<Class<?>> ALLOWED_TYPES = Set.of(
            Integer.class, Vector2i.class, Vector3i.class, Vector4i.class,
            Float.class, Vector2f.class, Vector3f.class, Vector4f.class,
            Matrix2f.class, Matrix3f.class, Matrix4f.class
    );

    public ValueGetter(BiFunction<GraphicsInstance<?>, Identifier, T> valueGetter, Class<T> clazz) {
        if (!ALLOWED_TYPES.contains(clazz)) {
            throw new IllegalArgumentException("Type not supported: " + clazz);
        }
        this.valueGetter = valueGetter;
    }

    public static <T> ValueGetter<T> create(T value, Class<T> clazz) {
        return new ValueGetter<>((graph, identifier) -> value, clazz);
    }

    public static <T> ValueGetter<T> create(BiFunction<GraphicsInstance<?>, Identifier, T> value, Class<T> clazz) {
        return new ValueGetter<>(value, clazz);
    }

    public T get(GraphicsInstance<?> graph, Identifier identifier) {
        return valueGetter.apply(graph, identifier);
    }
}
