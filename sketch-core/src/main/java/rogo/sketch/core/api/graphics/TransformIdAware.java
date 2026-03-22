package rogo.sketch.core.api.graphics;

/**
 * Optional callback for graphics that need the shared transform ID for vertex emission.
 */
public interface TransformIdAware {
    void setTransformId(int transformId);
}
