package rogo.sketch.render.pipeline;

import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.render.pipeline.container.AABBTreeContainer;
import rogo.sketch.render.pipeline.container.GraphicsContainer;
import rogo.sketch.render.pipeline.container.OctreeContainer;
import rogo.sketch.render.pipeline.container.QueueContainer;
import rogo.sketch.render.pool.InstancePoolManager;
import rogo.sketch.util.KeyId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class GraphicsBatch<C extends RenderContext> {
    public static final KeyId QUEUE_CONTAINER = KeyId.of("queue");
    public static final KeyId AABB_TREE_CONTAINER = KeyId.of("aabb_tree");
    public static final KeyId OCTREE_CONTAINER = KeyId.of("octree");
    public static final KeyId DEFAULT_CONTAINER = QUEUE_CONTAINER;

    private final Map<KeyId, GraphicsContainer<C>> containers = new LinkedHashMap<>();

    public GraphicsBatch() {
        // Pre-create all three standard containers
        containers.put(QUEUE_CONTAINER, new QueueContainer<>());
        containers.put(AABB_TREE_CONTAINER, new AABBTreeContainer<>());
        containers.put(OCTREE_CONTAINER, new OctreeContainer<>());
    }

    public void addGraphInstance(Graphics graph) {
        addGraphInstance(graph, DEFAULT_CONTAINER);
    }

    public void addGraphInstance(Graphics graph, KeyId containerType) {
        GraphicsContainer<C> container = containers.get(containerType);
        if (container == null) {
            throw new IllegalArgumentException("Unknown container type: " + containerType +
                    ". Use one of: QUEUE_CONTAINER, AABB_TREE_CONTAINER, OCTREE_CONTAINER");
        }
        container.add(graph);
    }

    public void tick(C context) {
        for (GraphicsContainer<C> container : containers.values()) {
            container.tick(context);
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
     * Cleanup discarded instances and return them to the pool
     */
    public void cleanupDiscardedInstances(InstancePoolManager poolManager) {
        for (GraphicsContainer<C> container : containers.values()) {
            Collection<Graphics> all = container.getAllInstances();
            for (Graphics instance : all) {
                if (instance.shouldDiscard()) {
                    container.remove(instance.getIdentifier());
                    poolManager.returnInstance(instance);
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
