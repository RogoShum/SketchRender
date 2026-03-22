package rogo.sketch.core.transform;

import org.joml.Vector3fc;

/**
 * Mutable authoring view for local transform state.
 */
public interface TransformWriter {
    void setPosition(float x, float y, float z);

    void setPosition(Vector3fc pos);

    void setRotation(float pitch, float yaw, float roll);

    void setRotation(Vector3fc rot);

    void setRotationDegrees(float pitchDeg, float yawDeg, float rollDeg);

    void setScale(float x, float y, float z);

    void setScale(float scale);

    void setScale(Vector3fc scale);

    void setPivot(float x, float y, float z);

    void setPivot(Vector3fc pivot);

    void reset();
}
