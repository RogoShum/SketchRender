package rogo.sketch.core.shader.uniform;

import org.jetbrains.annotations.Nullable;
import org.joml.*;

import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class ValueGetter<T> {
    private final Function<Object, T> valueGetter;
    private final Class<T> valueClass;
    private final Set<Class<?>> targetClasses;
    private final UniformCaptureTiming timing;

    private static final Set<Class<?>> ALLOWED_TYPES = Set.of(
            Integer.class, Vector2i.class, Vector3i.class, Vector4i.class,
            Float.class, Vector2f.class, Vector3f.class, Vector4f.class,
            Matrix2f.class, Matrix3f.class, Matrix4f.class,
            int[].class, Vector2i[].class, Vector3i[].class, Vector4i[].class,
            float[].class, Vector2f[].class, Vector3f[].class, Vector4f[].class,
            Matrix2f[].class, Matrix3f[].class, Matrix4f[].class
    );

    public ValueGetter(Function<Object, T> valueGetter, Class<T> clazz) {
        this(valueGetter, clazz, Collections.emptySet(), UniformCaptureTiming.PER_DRAW_DEFERRED);
    }

    public ValueGetter(Function<Object, T> valueGetter, Class<T> clazz, Set<Class<?>> targetClasses) {
        this(valueGetter, clazz, targetClasses, UniformCaptureTiming.PER_DRAW_DEFERRED);
    }

    public ValueGetter(Function<Object, T> valueGetter, Class<T> clazz, Set<Class<?>> targetClasses, UniformCaptureTiming timing) {
        if (!ALLOWED_TYPES.contains(clazz)) {
            throw new IllegalArgumentException("Type not supported: " + clazz);
        }
        this.valueGetter = valueGetter;
        this.valueClass = clazz;
        this.targetClasses = targetClasses;
        this.timing = timing != null ? timing : UniformCaptureTiming.PER_DRAW_DEFERRED;
    }

    public ValueGetter(Function<Object, T> valueGetter, Class<T> clazz, Set<Class<?>> targetClasses, UniformUpdateDomain domain) {
        this(valueGetter, clazz, targetClasses, domain != null ? domain.timing() : UniformCaptureTiming.PER_DRAW_DEFERRED);
    }

    public static <T> ValueGetter<T> create(Supplier<T> value, Class<T> clazz) {
        return new ValueGetter<>((graph) -> value.get(), clazz);
    }

    public static <T> ValueGetter<T> create(Supplier<T> value, Class<T> clazz, UniformCaptureTiming timing) {
        return new ValueGetter<>((graph) -> value.get(), clazz, Collections.emptySet(), timing);
    }

    public static <T> ValueGetter<T> create(Supplier<T> value, Class<T> clazz, UniformUpdateDomain domain) {
        return new ValueGetter<>((graph) -> value.get(), clazz, Collections.emptySet(), domain);
    }

    public static <T> ValueGetter<T> create(Function<Object, T> value, Class<T> clazz) {
        return new ValueGetter<>(value, clazz);
    }

    public static <T> ValueGetter<T> create(Function<Object, T> value, Class<T> clazz, UniformCaptureTiming timing) {
        return new ValueGetter<>(value, clazz, Collections.emptySet(), timing);
    }

    public static <T> ValueGetter<T> create(Function<Object, T> value, Class<T> clazz, UniformUpdateDomain domain) {
        return new ValueGetter<>(value, clazz, Collections.emptySet(), domain);
    }

    public static <T> ValueGetter<T> create(Function<Object, T> value, Class<T> clazz, Class<?>... targetClasses) {
        return new ValueGetter<>(value, clazz, Set.of(targetClasses));
    }

    public static <T> ValueGetter<T> create(Function<Object, T> value, Class<T> clazz, UniformCaptureTiming timing, Class<?>... targetClasses) {
        return new ValueGetter<>(value, clazz, Set.of(targetClasses), timing);
    }

    public static <T> ValueGetter<T> create(Function<Object, T> value, Class<T> clazz, UniformUpdateDomain domain, Class<?>... targetClasses) {
        return new ValueGetter<>(value, clazz, Set.of(targetClasses), domain);
    }

    public static <T> ValueGetter<T> create(Function<Object, T> value, Class<T> clazz, Set<Class<?>> targetClasses) {
        return new ValueGetter<>(value, clazz, targetClasses);
    }

    public static <T> ValueGetter<T> create(Function<Object, T> value, Class<T> clazz, Set<Class<?>> targetClasses, UniformCaptureTiming timing) {
        return new ValueGetter<>(value, clazz, targetClasses, timing);
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

    public UniformCaptureTiming timing() {
        return timing;
    }

    @Deprecated
    public UniformUpdateDomain domain() {
        return UniformUpdateDomain.fromTiming(timing);
    }

    public ValueGetter<T> withTiming(UniformCaptureTiming timing) {
        return new ValueGetter<>(valueGetter, valueClass, targetClasses, timing);
    }

    @Nullable
    public T get(Object graph) {
        return valueGetter.apply(graph);
    }
}
