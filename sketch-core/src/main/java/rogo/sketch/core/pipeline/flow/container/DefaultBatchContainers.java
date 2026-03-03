package rogo.sketch.core.pipeline.flow.container;

import rogo.sketch.core.api.graphics.AABBGraphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.container.AABBTreeContainer;
import rogo.sketch.core.pipeline.container.GraphicsContainer;
import rogo.sketch.core.pipeline.container.OctreeContainer;
import rogo.sketch.core.pipeline.container.PriorityContainer;
import rogo.sketch.core.pipeline.container.QueueContainer;
import rogo.sketch.core.util.KeyId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Built-in container descriptors for batch containers.
 */
public final class DefaultBatchContainers {
    public static final KeyId QUEUE = KeyId.of("queue");
    public static final KeyId AABB_TREE = KeyId.of("aabb_tree");
    public static final KeyId OCTREE = KeyId.of("octree");
    public static final KeyId PRIORITY = KeyId.of("priority");

    public static final KeyId DEFAULT = QUEUE;

    public static final ContainerDescriptor<RenderContext> QUEUE_DESCRIPTOR =
            ContainerDescriptor.of(QUEUE, containerSupplier(QueueContainer::new), (graphics, ignored) -> true);
    public static final ContainerDescriptor<RenderContext> AABB_TREE_DESCRIPTOR =
            ContainerDescriptor.of(AABB_TREE, containerSupplier(AABBTreeContainer::new),
                    (graphics, ignored) -> graphics instanceof AABBGraphics);
    public static final ContainerDescriptor<RenderContext> OCTREE_DESCRIPTOR =
            ContainerDescriptor.of(OCTREE, containerSupplier(OctreeContainer::new),
                    (graphics, ignored) -> graphics instanceof AABBGraphics);
    public static final ContainerDescriptor<RenderContext> PRIORITY_DESCRIPTOR =
            ContainerDescriptor.of(PRIORITY, containerSupplier(PriorityContainer::new), (graphics, ignored) -> true);

    private static final Map<KeyId, ContainerDescriptor<RenderContext>> DESCRIPTORS;
    private static final Map<KeyId, Integer> ORDERS;

    static {
        Map<KeyId, ContainerDescriptor<RenderContext>> map = new LinkedHashMap<>();
        map.put(QUEUE, QUEUE_DESCRIPTOR);
        map.put(AABB_TREE, AABB_TREE_DESCRIPTOR);
        map.put(OCTREE, OCTREE_DESCRIPTOR);
        map.put(PRIORITY, PRIORITY_DESCRIPTOR);
        DESCRIPTORS = Collections.unmodifiableMap(map);

        Map<KeyId, Integer> orderMap = new LinkedHashMap<>();
        orderMap.put(PRIORITY, 0);
        orderMap.put(QUEUE, 1);
        orderMap.put(AABB_TREE, 2);
        orderMap.put(OCTREE, 3);
        ORDERS = Collections.unmodifiableMap(orderMap);
    }

    private DefaultBatchContainers() {
    }

    public static Map<KeyId, ContainerDescriptor<RenderContext>> all() {
        return DESCRIPTORS;
    }

    public static Optional<ContainerDescriptor<RenderContext>> find(KeyId id) {
        return Optional.ofNullable(DESCRIPTORS.get(id));
    }

    public static int orderOf(KeyId id) {
        return ORDERS.getOrDefault(id, Integer.MAX_VALUE);
    }

    @SuppressWarnings("unchecked")
    private static Supplier<GraphicsContainer<RenderContext>> containerSupplier(
            Supplier<? extends GraphicsContainer<? extends RenderContext>> supplier) {
        return () -> (GraphicsContainer<RenderContext>) supplier.get();
    }
}

