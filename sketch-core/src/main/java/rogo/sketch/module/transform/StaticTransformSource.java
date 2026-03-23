package rogo.sketch.module.transform;

/**
 * Graphics with transform state that is authored once and then treated as static.
 */
public interface StaticTransformSource {
    void writeStaticTransform(TransformWriter writer);
}
