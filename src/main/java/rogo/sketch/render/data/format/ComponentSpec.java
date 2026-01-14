package rogo.sketch.render.data.format;

import java.util.Objects;

/**
 * Describes a VBO component specification.
 */
public class ComponentSpec {
    private final int bindingPoint;
    private final DataFormat format;
    private final boolean instanced;
    private final boolean mutable;

    public ComponentSpec(int bindingPoint, DataFormat format, boolean instanced, boolean mutable) {
        this.bindingPoint = bindingPoint;
        this.format = format;
        this.instanced = instanced;
        this.mutable = mutable;
    }

    /**
     * Create a mutable component (needs data filling).
     */
    public static ComponentSpec mutable(int bindingPoint, DataFormat format, boolean instanced) {
        return new ComponentSpec(bindingPoint, format, instanced, true);
    }

    /**
     * Create an immutable component (pre-baked, doesn't need filling).
     */
    public static ComponentSpec immutable(int bindingPoint, DataFormat format, boolean instanced) {
        return new ComponentSpec(bindingPoint, format, instanced, false);
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

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ComponentSpec that = (ComponentSpec) o;
        return bindingPoint == that.bindingPoint &&
                Objects.equals(format, that.format) &&
                instanced == that.instanced &&
                mutable == that.mutable;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bindingPoint, format, instanced, mutable);
    }

    @Override
    public String toString() {
        return String.format("ComponentSpec{binding=%d, %s, instanced=%s, mutable=%s}",
                bindingPoint, format.getName(), instanced, mutable);
    }
}
