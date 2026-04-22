package rogo.sketch.module.transform;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import rogo.sketch.core.util.GraphicsData;
import rogo.sketch.core.data.builder.UnsafeHelper;
import rogo.sketch.core.graphics.ecs.TransformWriter;
import sun.misc.Unsafe;

/**
 * Triple-buffered transform data for thread-safe async updates.
 * Contains previous and current TRS (Translation, Rotation, Scale) data
 * for GPU interpolation.
 * <p>
 * Rotation uses Euler angles in radians with ZYX convention (Roll-Yaw-Pitch).
 */
public class TransformData extends GraphicsData<TransformData> implements TransformWriter {
    public static final int FLAG_HAS_PARENT = 1;
    public static final int FLAG_HAS_PIVOT = 1 << 1;
    public static final int FLAG_IDENTITY_ROTATION = 1 << 2;
    public static final int FLAG_IDENTITY_SCALE = 1 << 3;
    private static final float EPSILON = 1.0e-6f;

    // Previous frame transform data (for interpolation)
    public final Vector3f pos = new Vector3f();
    public final Vector3f rot = new Vector3f(); // Euler angles (pitch, yaw, roll) in radians
    public final Vector3f scale = new Vector3f(1, 1, 1);
    // Pivot point for rotation
    public final Vector3f pivot = new Vector3f();

    /**
     * Set the current position.
     */
    public void setPosition(float x, float y, float z) {
        pos.set(x, y, z);
    }

    /**
     * Set the current position.
     */
    public void setPosition(Vector3fc pos) {
        this.pos.set(pos);
    }

    /**
     * Set the current rotation from Euler angles (in radians).
     * Uses ZYX convention (Roll-Yaw-Pitch).
     *
     * @param pitch Rotation around X axis (radians)
     * @param yaw   Rotation around Y axis (radians)
     * @param roll  Rotation around Z axis (radians)
     */
    public void setRotation(float pitch, float yaw, float roll) {
        rot.set(pitch, yaw, roll);
    }

    /**
     * Set the current rotation from Euler angles vector.
     *
     * @param rot Vector containing (pitch, yaw, roll) in radians
     */
    public void setRotation(Vector3fc rot) {
        this.rot.set(rot);
    }

    /**
     * Set the current rotation from degrees.
     *
     * @param pitchDeg Rotation around X axis (degrees)
     * @param yawDeg   Rotation around Y axis (degrees)
     * @param rollDeg  Rotation around Z axis (degrees)
     */
    public void setRotationDegrees(float pitchDeg, float yawDeg, float rollDeg) {
        rot.set(
                (float) Math.toRadians(pitchDeg),
                (float) Math.toRadians(yawDeg),
                (float) Math.toRadians(rollDeg)
        );
    }

    /**
     * Set the current scale.
     */
    public void setScale(float x, float y, float z) {
        scale.set(x, y, z);
    }

    /**
     * Set the current scale uniformly.
     */
    public void setScale(float scale) {
        this.scale.set(scale, scale, scale);
    }

    /**
     * Set the current scale.
     */
    public void setScale(Vector3fc scale) {
        this.scale.set(scale);
    }

    /**
     * Set the pivot point.
     */
    public void setPivot(float x, float y, float z) {
        pivot.set(x, y, z);
    }

    /**
     * Set the pivot point.
     */
    public void setPivot(Vector3fc pivot) {
        this.pivot.set(pivot);
    }

    @Override
    public void copyFrom(TransformData other) {
        this.pos.set(other.pos);
        this.rot.set(other.rot);
        this.scale.set(other.scale);
        this.pivot.set(other.pivot);
    }

    /**
     * Reset to identity transform.
     */
    public void reset() {
        pos.set(0, 0, 0);
        rot.set(0, 0, 0);
        scale.set(1, 1, 1);
        pivot.set(0, 0, 0);
    }

    public static int computeFlags(TransformData previous, TransformData current, int parentId) {
        int flags = 0;
        if (parentId >= 0) {
            flags |= FLAG_HAS_PARENT;
        }
        if (!isApproximatelyZero(current.pivot.x)
                || !isApproximatelyZero(current.pivot.y)
                || !isApproximatelyZero(current.pivot.z)) {
            flags |= FLAG_HAS_PIVOT;
        }
        if (isApproximatelyZero(previous.rot.x) && isApproximatelyZero(previous.rot.y) && isApproximatelyZero(previous.rot.z)
                && isApproximatelyZero(current.rot.x) && isApproximatelyZero(current.rot.y) && isApproximatelyZero(current.rot.z)) {
            flags |= FLAG_IDENTITY_ROTATION;
        }
        if (isApproximatelyOne(previous.scale.x) && isApproximatelyOne(previous.scale.y) && isApproximatelyOne(previous.scale.z)
                && isApproximatelyOne(current.scale.x) && isApproximatelyOne(current.scale.y) && isApproximatelyOne(current.scale.z)) {
            flags |= FLAG_IDENTITY_SCALE;
        }
        return flags;
    }

    public static void writeToBuffer(
            TransformData previous,
            TransformData current,
            long ptr,
            int parentId,
            int selfId,
            int flags
    ) {
        Unsafe unsafe = UnsafeHelper.getUnsafe();
        long base = ptr;

        unsafe.putFloat(base + 0, previous.pos.x);
        unsafe.putFloat(base + 4, previous.pos.y);
        unsafe.putFloat(base + 8, previous.pos.z);

        unsafe.putFloat(base + 16, previous.rot.x);
        unsafe.putFloat(base + 20, previous.rot.y);
        unsafe.putFloat(base + 24, previous.rot.z);

        unsafe.putFloat(base + 32, previous.scale.x);
        unsafe.putFloat(base + 36, previous.scale.y);
        unsafe.putFloat(base + 40, previous.scale.z);

        unsafe.putFloat(base + 48, current.pos.x);
        unsafe.putFloat(base + 52, current.pos.y);
        unsafe.putFloat(base + 56, current.pos.z);

        unsafe.putFloat(base + 64, current.rot.x);
        unsafe.putFloat(base + 68, current.rot.y);
        unsafe.putFloat(base + 72, current.rot.z);

        unsafe.putFloat(base + 80, current.scale.x);
        unsafe.putFloat(base + 84, current.scale.y);
        unsafe.putFloat(base + 88, current.scale.z);

        unsafe.putFloat(base + 96, current.pivot.x);
        unsafe.putFloat(base + 100, current.pivot.y);
        unsafe.putFloat(base + 104, current.pivot.z);

        unsafe.putInt(base + 112, parentId);
        unsafe.putInt(base + 116, selfId);
        unsafe.putInt(base + 120, flags);
        unsafe.putInt(base + 124, 0);
    }

    private static boolean isApproximatelyZero(float value) {
        return Math.abs(value) <= EPSILON;
    }

    private static boolean isApproximatelyOne(float value) {
        return Math.abs(value - 1.0f) <= EPSILON;
    }
}
