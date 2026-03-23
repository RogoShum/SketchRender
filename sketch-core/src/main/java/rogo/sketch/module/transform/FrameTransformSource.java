package rogo.sketch.module.transform;

/**
 * Graphics that author transform state during the frame graph on the main thread.
 */
public interface FrameTransformSource {
    void writeFrameTransform(TransformWriter writer);
}
