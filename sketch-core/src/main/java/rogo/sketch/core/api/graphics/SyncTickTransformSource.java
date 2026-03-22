package rogo.sketch.core.api.graphics;

import rogo.sketch.core.transform.TransformWriter;

/**
 * Graphics that author transform state on the main thread during game tick.
 */
public interface SyncTickTransformSource {
    void writeSyncTickTransform(TransformWriter writer);
}
