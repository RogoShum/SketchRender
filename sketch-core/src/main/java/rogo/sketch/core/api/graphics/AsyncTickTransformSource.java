package rogo.sketch.core.api.graphics;

import rogo.sketch.core.transform.TransformWriter;

/**
 * Graphics that author transform state on the tick worker after sync tick work.
 */
public interface AsyncTickTransformSource {
    void writeAsyncTickTransform(TransformWriter writer);
}
