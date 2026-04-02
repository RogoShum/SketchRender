package rogo.sketch.core.pipeline.flow.v2;

/**
 * Bit-mask flags for per-instance cache invalidation in the V2 storage path.
 */
public final class InstanceDirtyMask {
    public static final int NONE = 0;
    public static final int DESCRIPTOR = 1;
    public static final int GEOMETRY = 1 << 1;
    public static final int BOUNDS = 1 << 2;
    public static final int MEMBERSHIP = 1 << 3;
    public static final int DISCARDED = 1 << 4;

    private InstanceDirtyMask() {
    }

    public static boolean has(int mask, int flag) {
        return (mask & flag) == flag;
    }

    public static int add(int mask, int flag) {
        return mask | flag;
    }

    public static int remove(int mask, int flag) {
        return mask & ~flag;
    }
}
