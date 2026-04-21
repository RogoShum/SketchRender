package rogo.sketch.core.pipeline.flow.ecs;

import rogo.sketch.core.util.KeyId;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ECS-native container ordering hints that replace legacy GraphicsContainer
 * descriptors in the stage-flow main path.
 */
public final class GraphicsContainerHints {
    public static final KeyId QUEUE = KeyId.of("queue");
    public static final KeyId AABB_TREE = KeyId.of("aabb_tree");
    public static final KeyId OCTREE = KeyId.of("octree");
    public static final KeyId PRIORITY = KeyId.of("priority");
    public static final KeyId DEFAULT = QUEUE;

    private static final Map<KeyId, Integer> ORDERS = new LinkedHashMap<>();

    static {
        ORDERS.put(PRIORITY, 0);
        ORDERS.put(QUEUE, 1);
        ORDERS.put(AABB_TREE, 2);
        ORDERS.put(OCTREE, 3);
    }

    private GraphicsContainerHints() {
    }

    public static int orderOf(KeyId containerType) {
        return ORDERS.getOrDefault(containerType != null ? containerType : DEFAULT, Integer.MAX_VALUE);
    }
}
