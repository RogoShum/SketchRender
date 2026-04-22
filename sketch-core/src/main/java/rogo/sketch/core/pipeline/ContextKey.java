package rogo.sketch.core.pipeline;

import rogo.sketch.core.util.KeyId;

import java.util.Objects;

public final class ContextKey<T> {
    private final KeyId id;
    private final Class<T> type;

    private ContextKey(KeyId id, Class<T> type) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> ContextKey<T> of(String id, Class<T> type) {
        return of(KeyId.of(id), type);
    }

    public static <T> ContextKey<T> of(KeyId id, Class<T> type) {
        return new ContextKey<>(id, type);
    }

    public KeyId id() {
        return id;
    }

    public Class<T> type() {
        return type;
    }

    T cast(Object value) {
        return value != null ? type.cast(value) : null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ContextKey<?> other)) {
            return false;
        }
        return id.equals(other.id) && type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
