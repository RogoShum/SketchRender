package rogo.sketch.core.pipeline.kernel;

import java.util.Objects;

/**
 * Typed identifier for data published through the kernel resource bus.
 *
 * @param <T> resource payload type
 */
public final class KernelResourceKey<T> {
    private final String id;
    private final Class<T> type;

    private KernelResourceKey(String id, Class<T> type) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> KernelResourceKey<T> of(String id, Class<T> type) {
        return new KernelResourceKey<>(id, type);
    }

    public String id() {
        return id;
    }

    public Class<T> type() {
        return type;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof KernelResourceKey<?> other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "KernelResourceKey[" + id + "]";
    }
}
