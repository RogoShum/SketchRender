package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.ResourceSetKey;

/**
 * Stable ordering key for draw packet emission inside a stage.
 */
public record DrawSortKey(
        String shaderKey,
        String renderTargetKey,
        String resourceLayoutKey,
        String resourceSetLayoutKey,
        int resourceBindingHash,
        int resourceUniformHash,
        String geometryKey,
        long firstVisibleOrder,
        String tieBreaker
) implements Comparable<DrawSortKey> {
    public static DrawSortKey of(
            ExecutionKey stateKey,
            ResourceSetKey resourceSetKey,
            GeometryHandleKey geometryHandle,
            long firstVisibleOrder,
            String tieBreaker) {
        return new DrawSortKey(
                stateKey != null && stateKey.shaderId() != null ? stateKey.shaderId().toString() : "",
                stateKey != null && stateKey.renderTargetKey() != null ? stateKey.renderTargetKey().toString() : "",
                stateKey != null && stateKey.resourceLayoutKey() != null ? stateKey.resourceLayoutKey().toString() : "",
                resourceSetKey != null && resourceSetKey.resourceLayoutKey() != null ? resourceSetKey.resourceLayoutKey().toString() : "",
                resourceSetKey != null ? resourceSetKey.resourceBindingHash() : 0,
                resourceSetKey != null ? resourceSetKey.resourceUniformHash() : 0,
                geometryHandle != null && geometryHandle.vertexBufferKey() != null
                        ? geometryHandle.vertexBufferKey().toString()
                        : "",
                firstVisibleOrder,
                tieBreaker != null ? tieBreaker : "");
    }

    @Override
    public int compareTo(DrawSortKey other) {
        if (other == null) {
            return 1;
        }
        int compare = shaderKey.compareTo(other.shaderKey);
        if (compare != 0) {
            return compare;
        }
        compare = renderTargetKey.compareTo(other.renderTargetKey);
        if (compare != 0) {
            return compare;
        }
        compare = resourceLayoutKey.compareTo(other.resourceLayoutKey);
        if (compare != 0) {
            return compare;
        }
        compare = resourceSetLayoutKey.compareTo(other.resourceSetLayoutKey);
        if (compare != 0) {
            return compare;
        }
        compare = Integer.compare(resourceBindingHash, other.resourceBindingHash);
        if (compare != 0) {
            return compare;
        }
        compare = Integer.compare(resourceUniformHash, other.resourceUniformHash);
        if (compare != 0) {
            return compare;
        }
        compare = geometryKey.compareTo(other.geometryKey);
        if (compare != 0) {
            return compare;
        }
        compare = Long.compare(firstVisibleOrder, other.firstVisibleOrder);
        if (compare != 0) {
            return compare;
        }
        return tieBreaker.compareTo(other.tieBreaker);
    }
}
