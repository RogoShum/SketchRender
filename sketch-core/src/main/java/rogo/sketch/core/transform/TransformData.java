package rogo.sketch.core.transform;

import org.joml.Vector3f;
import rogo.sketch.core.util.GraphicsData;

/**
 * Triple-buffered transform data for thread-safe async updates.
 * Contains previous and current TRS (Translation, Rotation, Scale) data
 * for GPU interpolation.
 * <p>
 * Rotation uses Euler angles in radians with ZYX convention (Roll-Yaw-Pitch).
 */
public class TransformData extends GraphicsData<TransformData> {
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
    public void setPosition(Vector3f pos) {
        rot.set(pos);
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
    public void setRotation(Vector3f rot) {
        rot.set(rot);
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
    public void setScale(Vector3f scale) {
        scale.set(scale);
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
    public void setPivot(Vector3f pivot) {
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
}