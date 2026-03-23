package rogo.sketch.module.transform;

/**
 * Graphics that author transform state on the main thread during game tick.
 */
public interface SyncTickTransformSource {
    void writeSyncTickTransform(TransformWriter writer);
}
