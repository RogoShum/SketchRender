package rogo.sketch.module.transform;

/**
 * Graphics that author transform state on the tick worker after sync tick work.
 */
public interface AsyncTickTransformSource {
    void writeAsyncTickTransform(TransformWriter writer);
}
