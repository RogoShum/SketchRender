package rogo.sketch.module.transform.manager;

/**
 * Compute dispatch range for one hierarchy depth layer.
 *
 * @param offset flattened batch start offset
 * @param count  number of transforms in this layer
 */
public record TransformDispatchRange(int offset, int count) {
}
