package rogo.sketch.core.shader.uniform;

import org.jetbrains.annotations.Nullable;
import org.joml.*;

import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class ValueGetter<T> {
    private final Function<Object, T> valueGetter;
    private final Set<Class<?>> targetClasses;
    private final UniformUpdateDomain domain;

    private static final Set<Class<?>> ALLOWED_TYPES = Set.of(
            Integer.class, Vector2i.class, Vector3i.class, Vector4i.class,
            Float.class, Vector2f.class, Vector3f.class, Vector4f.class,
            Matrix2f.class, Matrix3f.class, Matrix4f.class,
            int[].class, Vector2i[].class, Vector3i[].class, Vector4i[].class,
            float[].class, Vector2f[].class, Vector3f[].class, Vector4f[].class,
            Matrix2f[].class, Matrix3f[].class, Matrix4f[].class
    );

    public ValueGetter(Function<Object, T> valueGetter, Class<T> clazz) {
        this(valueGetter, clazz, Collections.emptySet(), UniformUpdateDomain.FRAME_LIVE);
    }

    public ValueGetter(Function<Object, T> valueGetter, Class<T> clazz, Set<Class<?>> targetClasses) {
        this(valueGetter, clazz, targetClasses, UniformUpdateDomain.FRAME_LIVE);
    }

    public ValueGetter(Function<Object, T> valueGetter, Class<T> clazz, Set<Class<?>> targetClasses, UniformUpdateDomain domain) {
        if (!ALLOWED_TYPES.contains(clazz)) {
            throw new IllegalArgumentException("Type not supported: " + clazz);
        }
        this.valueGetter = valueGetter;
        this.targetClasses = targetClasses;
        this.domain = domain != null ? domain : UniformUpdateDomain.FRAME_LIVE;
    }

    public static <T> ValueGetter<T> create(Supplier<T> value, Class<T> clazz) {
        return new ValueGetter<>((graph) -> value.get(), clazz);
    }

    public static <T> ValueGetter<T> create(Supplier<T> value, Class<T> clazz, UniformUpdateDomain domain) {
        return new ValueGetter<>((graph) -> value.get(), clazz, Collections.emptySet(), domain);
    }

    public static <T> ValueGetter<T> create(Function<Object, T> value, Class<T> clazz) {
        return new ValueGetter<>(value, clazz);
    }

    public static <T> ValueGetter<T> create(Function<Object, T> value, Class<T> clazz, UniformUpdateDomain domain) {
        return new ValueGetter<>(value, clazz, Collections.emptySet(), domain);
    }

    public static <T> ValueGetter<T> create(Function<Object, T> value, Class<T> clazz, Class<?>... targetClasses) {
        return new ValueGetter<>(value, clazz, Set.of(targetClasses));
    }

    public static <T> ValueGetter<T> create(Function<Object, T> value, Class<T> clazz, UniformUpdateDomain domain, Class<?>... targetClasses) {
        return new ValueGetter<>(value, clazz, Set.of(targetClasses), domain);
    }

    public static <T> ValueGetter<T> create(Function<Object, T> value, Class<T> clazz, Set<Class<?>> targetClasses) {
        return new ValueGetter<>(value, clazz, targetClasses);
    }

    public static <T> ValueGetter<T> create(Function<Object, T> value, Class<T> clazz, Set<Class<?>> targetClasses, UniformUpdateDomain domain) {
        return new ValueGetter<>(value, clazz, targetClasses, domain);
    }

    /**
     * Get the target classes this ValueGetter can work with
     */
    public Set<Class<?>> getTargetClasses() {
        return Collections.unmodifiableSet(targetClasses);
    }

    public UniformUpdateDomain domain() {
        return domain;
    }

    @Nullable
    public T get(Object graph) {
        return valueGetter.apply(graph);
    }
}
