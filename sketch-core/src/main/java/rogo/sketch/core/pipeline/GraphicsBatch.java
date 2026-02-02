package rogo.sketch.core.pipeline;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.container.*;
import rogo.sketch.core.pipeline.flow.BatchContainer;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class GraphicsBatch<C extends RenderContext> {
    public static final KeyId QUEUE_CONTAINER = KeyId.of("queue");
    public static final KeyId AABB_TREE_CONTAINER = KeyId.of("aabb_tree");
    public static final KeyId OCTREE_CONTAINER = KeyId.of("octree");
    public static final KeyId PRIORITY_CONTAINER = KeyId.of("priority");
    public static final KeyId DEFAULT_CONTAINER = QUEUE_CONTAINER;

    private final Map<KeyId, GraphicsContainer<C>> containers = new LinkedHashMap<>();

    public GraphicsBatch() {
        // Pre-create all three standard containers
        containers.put(QUEUE_CONTAINER, new QueueContainer<>());
        containers.put(AABB_TREE_CONTAINER, new AABBTreeContainer<>());
        containers.put(OCTREE_CONTAINER, new OctreeContainer<>());
        containers.put(PRIORITY_CONTAINER, new PriorityContainer<>());
    }

    public void addGraphInstance(Graphics graph) {
        addGraphInstance(graph, DEFAULT_CONTAINER);
    }

    public void addGraphInstance(Graphics graph, KeyId containerType) {
        addGraphInstance(graph, containerType, null);
    }

    public void addGraphInstance(Graphics graph, KeyId containerType, RenderParameter renderParameter) {
        GraphicsContainer<C> container = containers.get(containerType);
        if (container == null) {
            throw new IllegalArgumentException("Unknown container type: " + containerType +
                    ". Use one of: QUEUE_CONTAINER, AABB_TREE_CONTAINER, OCTREE_CONTAINER");
        }
        container.add(graph, renderParameter);
    }

    /**
     * Register a BatchContainer as listener for all containers in this batch.
     */
    public void registerBatchContainerListener(BatchContainer<?, ?> batchContainer) {
        for (GraphicsContainer<C> container : containers.values()) {
            container.addListener(batchContainer);
        }
    }

    /**
     * Unregister a BatchContainer listener from all containers.
     */
    public void unregisterBatchContainerListener(BatchContainer<?, ?> batchContainer) {
        for (GraphicsContainer<C> container : containers.values()) {
            container.removeListener(batchContainer);
        }
    }

    public void tick(C context) {
        for (GraphicsContainer<C> container : containers.values()) {
            container.tick(context);
        }
    }

    public void asyncTick(C context) {
        for (GraphicsContainer<C> container : containers.values()) {
            container.asyncTick(context);
            container.dirtyCheck();
        }
    }

    public void swapData() {
        for (GraphicsContainer<C> container : containers.values()) {
            container.swapData();
        }
    }

    public Collection<Graphics> getAllInstances() {
        Collection<Graphics> all = new ArrayList<>();
        for (GraphicsContainer<C> container : containers.values()) {
            all.addAll(container.getAllInstances());
        }
        return all;
    }

    public Collection<Graphics> getVisibleInstances(C context) {
        Collection<Graphics> visible = new ArrayList<>();
        for (GraphicsContainer<C> container : containers.values()) {
            visible.addAll(container.getVisibleInstances(context));
        }
        return visible;
    }

    /**
     * Cleanup discarded instances and return them to the pool.
     * Also clears batch assignments for discarded instances.
     */
    public void cleanupDiscardedInstances() {
        for (GraphicsContainer<C> container : containers.values()) {
            Collection<Graphics> all = new ArrayList<>(container.getAllInstances());
            for (Graphics instance : all) {
                if (instance.shouldDiscard()) {
                    container.remove(instance.getIdentifier());
                }
            }
        }
    }

    /**
     * Get a specific container by type.
     *
     * @param containerType Container identifier
     * @return Graphics container
     */
    public GraphicsContainer<C> getContainer(KeyId containerType) {
        return containers.get(containerType);
    }
}