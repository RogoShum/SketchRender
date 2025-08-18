package rogo.sketch.render.shader.uniform;

import org.joml.*;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class ValueGetter<T> {
    private final Function<Object, T> valueGetter;

    private static final Set<Class<?>> ALLOWED_TYPES = Set.of(
            Integer.class, Vector2i.class, Vector3i.class, Vector4i.class,
            Float.class, Vector2f.class, Vector3f.class, Vector4f.class,
            Matrix2f.class, Matrix3f.class, Matrix4f.class,
            int[].class, Vector2i[].class, Vector3i[].class, Vector4i[].class,
            float[].class, Vector2f[].class, Vector3f[].class, Vector4f[].class,
            Matrix2f[].class, Matrix3f[].class, Matrix4f[].class
    );

    public ValueGetter(Function<Object, T> valueGetter, Class<T> clazz) {
        if (!ALLOWED_TYPES.contains(clazz)) {
            throw new IllegalArgumentException("Type not supported: " + clazz);
        }
        this.valueGetter = valueGetter;
    }

    public static <T> ValueGetter<T> create(Supplier<T> value, Class<T> clazz) {
        return new ValueGetter<>((graph) -> value.get(), clazz);
    }

    public static <T> ValueGetter<T> create(Function<Object, T> value, Class<T> clazz) {
        return new ValueGetter<>(value, clazz);
    }

    @Nullable
    public T get(Object graph) {
        return valueGetter.apply(graph);
    }
}