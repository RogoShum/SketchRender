package rogo.sketch.core.object;

import rogo.sketch.core.util.KeyId;

import java.util.Objects;

/**
 * Stable externally-visible root role used to resolve internal graphics roots.
 */
public final class ObjectGraphicsRootRole {
    public static final ObjectGraphicsRootRole PRIMARY =
            new ObjectGraphicsRootRole(KeyId.of("sketch", "primary_root"));

    private final KeyId id;

    private ObjectGraphicsRootRole(KeyId id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public static ObjectGraphicsRootRole of(KeyId id) {
        return new ObjectGraphicsRootRole(id);
    }

    public KeyId id() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ObjectGraphicsRootRole other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
