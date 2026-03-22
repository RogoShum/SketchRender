package rogo.sketch.core.transform;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import rogo.sketch.core.util.GraphicsData;
import rogo.sketch.core.data.builder.UnsafeHelper;
import sun.misc.Unsafe;

/**
 * Triple-buffered transform data for thread-safe async updates.
 * Contains previous and current TRS (Translation, Rotation, Scale) data
 * for GPU interpolation.
 * <p>
 * Rotation uses Euler angles in radians with ZYX convention (Roll-Yaw-Pitch).
 */
public class TransformData extends GraphicsData<TransformData> implements TransformWriter {
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

    public static void writeToBuffer(TransformData previous, TransformData current, long ptr, int parentId) {
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
        unsafe.putInt(base + 116, 0);
        unsafe.putInt(base + 120, 0);
        unsafe.putInt(base + 124, 0);
    }
}