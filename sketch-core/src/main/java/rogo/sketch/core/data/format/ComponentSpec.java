package rogo.sketch.core.data.format;

import java.util.Objects;
import rogo.sketch.core.util.KeyId;

/**
 * Describes a VBO component specification.
 */
public class ComponentSpec {
    private final KeyId id;
    private final int bindingPoint;
    private final DataFormat format;
    private final boolean instanced;
    private final boolean mutable;
    private final boolean tickUpdate;

    protected ComponentSpec(KeyId id, int bindingPoint, DataFormat format, boolean instanced, boolean mutable, boolean tickUpdate) {
        this.id = id;
        this.bindingPoint = bindingPoint;
        this.format = format;
        this.instanced = instanced;
        this.mutable = mutable;
        this.tickUpdate = tickUpdate;
    }

    /**
     * Create a mutable component (needs data filling).
     */
    public static ComponentSpec mutable(KeyId id, int bindingPoint, DataFormat format, boolean instanced) {
        return new ComponentSpec(id, bindingPoint, format, instanced, true, false);
    }

    /**
     * Create an immutable component (pre-baked, doesn't need filling).
     */
    public static ComponentSpec immutable(KeyId id, int bindingPoint, DataFormat format, boolean instanced) {
        return new ComponentSpec(id, bindingPoint, format, instanced, false, false);
    }

    public KeyId getId() {
        return id;
    }

    public int getBindingPoint() {
        return bindingPoint;
    }

    public DataFormat getFormat() {
        return format;
    }

    public boolean isInstanced() {
        return instanced;
    }

    public boolean isMutable() {
        return mutable;
    }

    public boolean isImmutable() {
        return !mutable;
    }

    public boolean isTickUpdate() {
        return tickUpdate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ComponentSpec that = (ComponentSpec) o;
        return bindingPoint == that.bindingPoint &&
                Objects.equals(id, that.id) &&
                Objects.equals(format, that.format) &&
                instanced == that.instanced &&
                mutable == that.mutable &&
                tickUpdate == that.tickUpdate;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, bindingPoint, format, instanced, mutable, tickUpdate);
    }

    @Override
    public String toString() {
        return String.format("ComponentSpec{id=%s, binding=%d, format=%s, instanced=%s, mutable=%s}",
                id, bindingPoint, format.getName(), instanced, mutable);
    }
}