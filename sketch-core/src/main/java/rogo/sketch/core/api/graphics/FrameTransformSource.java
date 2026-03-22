package rogo.sketch.core.api.graphics;

import rogo.sketch.core.transform.TransformWriter;

/**
 * Graphics that author transform state during the frame graph on the main thread.
 */
public interface FrameTransformSource {
    void writeFrameTransform(TransformWriter writer);
}
