package rogo.sketch.core.pipeline.container;

import org.joml.FrustumIntersection;
import org.joml.primitives.AABBf;
import rogo.sketch.core.api.graphics.AABBGraphics;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

import java.util.*;

/**
 * Octree-based graphics container with spatial partitioning and frustum
 * culling.
 * Uses JOML primitives for cross-platform compatibility.
 * Requires graphics instances to implement {@link AABBGraphics}.
 */
public class OctreeContainer<C extends RenderContext> implements GraphicsContainer<C> {
    private static final int MAX_DEPTH = 8;
    private static final int MAX_OBJECTS_PER_NODE = 8;

    private final Map<KeyId, Graphics> instanceMap = new LinkedHashMap<>();
    private final Collection<Graphics> tickableInstances = new ArrayList<>();
    private OctreeNode root;

    public OctreeContainer() {
        // Initialize with a large default bounds - will expand as needed
        root = new OctreeNode(new AABBf(-1000, -1000, -1000, 1000, 1000, 1000), 0);
    }

    public OctreeContainer(AABBf worldBounds) {
        root = new OctreeNode(new AABBf(worldBounds), 0);
    }

    @Override
    public void add(Graphics graphics) {
        if (!(graphics instanceof AABBGraphics)) {
            throw new IllegalArgumentException(
                    "Graphics instance must implement AABBObject to be added to OctreeContainer: " +
                            graphics.getClass().getName());
        }

        AABBGraphics aabbGraphics = (AABBGraphics) graphics;
        AABBf bounds = aabbGraphics.getAABB();

        if (bounds == null) {
            throw new IllegalArgumentException(
                    "AABBObject.getAABB() returned null for instance: " + graphics.getIdentifier());
        }

        instanceMap.put(graphics.getIdentifier(), graphics);
        if (graphics.tickable()) {
            tickableInstances.add(graphics);
        }
        root.insert(graphics, bounds);
    }

    @Override
    public void remove(KeyId identifier) {
        Graphics graphics = instanceMap.remove(identifier);
        if (graphics != null) {
            if (graphics.tickable()) {
                tickableInstances.remove(graphics);
            }
            if (graphics instanceof AABBGraphics) {
                AABBf bounds = ((AABBGraphics) graphics).getAABB();
                if (bounds != null) {
                    root.remove(graphics, bounds);
                    // Optionally try to merge nodes
                    root.tryMerge();
                }
            }
        }
    }

    @Override
    public void tick(C context) {
        // Check for AABB changes and reinsert if necessary
        List<Graphics> toReinsert = new ArrayList<>();

        for (Graphics graphics : instanceMap.values()) {
            if (graphics instanceof AABBGraphics) {
                AABBf newBounds = ((AABBGraphics) graphics).getAABB();
                if (newBounds != null) {
                    // For simplicity, reinsert all objects each tick
                    // More sophisticated implementations could track changes
                    toReinsert.add(graphics);
                }
            }
        }

        for (Graphics graphics : tickableInstances) {
            if (graphics.shouldTick()) {
                graphics.tick(context);
            }
        }

        // Rebuild tree if needed (simple approach - could be optimized)
        if (!toReinsert.isEmpty()) {
            root = new OctreeNode(root.bounds, 0);
            for (Graphics graphics : toReinsert) {
                AABBf bounds = ((AABBGraphics) graphics).getAABB();
                root.insert(graphics, bounds);
            }
        }

        // Cleanup discarded instances
        java.util.List<KeyId> toRemove = new java.util.ArrayList<>();
        for (Graphics graphics : instanceMap.values()) {
            if (graphics.shouldDiscard()) {
                toRemove.add(graphics.getIdentifier());
            }
        }

        for (KeyId id : toRemove) {
            remove(id);
        }
    }

    @Override
    public Collection<Graphics> getAllInstances() {
        return new ArrayList<>(instanceMap.values());
    }

    @Override
    public Collection<Graphics> getVisibleInstances(C context) {
        FrustumIntersection frustum = context.getFrustum();
        if (frustum == null || root == null) {
            // No frustum available - return all instances that should render
            List<Graphics> all = new ArrayList<>();
            for (Graphics graphics : instanceMap.values()) {
                if (graphics.shouldRender()) {
                    all.add(graphics);
                }
            }
            return all;
        }

        List<Graphics> visible = new ArrayList<>();
        root.collectVisible(frustum, visible);
        return visible;
    }

    @Override
    public void clear() {
        instanceMap.clear();
        root = new OctreeNode(root.bounds, 0);
    }

    private static class OctreeNode {
        AABBf bounds;
        int depth;
        List<Graphics> objects = new ArrayList<>();
        OctreeNode[] children = null; // 8 children for octree

        OctreeNode(AABBf bounds, int depth) {
            this.bounds = bounds;
            this.depth = depth;
        }

        void insert(Graphics graphics, AABBf graphicsBounds) {
            // If this node is subdivided, try to insert into children
            if (children != null) {
                int childIndex = getChildIndex(graphicsBounds);
                if (childIndex != -1) {
                    children[childIndex].insert(graphics, graphicsBounds);
                    return;
                }
                // Doesn't fit into any child, store here
            }

            objects.add(graphics);

            // Subdivide if necessary
            if (objects.size() > MAX_OBJECTS_PER_NODE && depth < MAX_DEPTH && children == null) {
                subdivide();
                // Try to move objects to children
                List<Graphics> remaining = new ArrayList<>();
                for (Graphics obj : objects) {
                    if (obj instanceof AABBGraphics) {
                        AABBf objBounds = ((AABBGraphics) obj).getAABB();
                        if (objBounds != null) {
                            int childIndex = getChildIndex(objBounds);
                            if (childIndex != -1) {
                                children[childIndex].insert(obj, objBounds);
                            } else {
                                remaining.add(obj);
                            }
                        }
                    }
                }
                objects = remaining;
            }
        }

        void remove(Graphics graphics, AABBf graphicsBounds) {
            objects.remove(graphics);

            if (children != null) {
                int childIndex = getChildIndex(graphicsBounds);
                if (childIndex != -1) {
                    children[childIndex].remove(graphics, graphicsBounds);
                }
            }
        }

        void subdivide() {
            float midX = (bounds.minX + bounds.maxX) / 2;
            float midY = (bounds.minY + bounds.maxY) / 2;
            float midZ = (bounds.minZ + bounds.maxZ) / 2;

            children = new OctreeNode[8];
            children[0] = new OctreeNode(new AABBf(bounds.minX, bounds.minY, bounds.minZ, midX, midY, midZ), depth + 1);
            children[1] = new OctreeNode(new AABBf(midX, bounds.minY, bounds.minZ, bounds.maxX, midY, midZ), depth + 1);
            children[2] = new OctreeNode(new AABBf(bounds.minX, bounds.minY, midZ, midX, midY, bounds.maxZ), depth + 1);
            children[3] = new OctreeNode(new AABBf(midX, bounds.minY, midZ, bounds.maxX, midY, bounds.maxZ), depth + 1);
            children[4] = new OctreeNode(new AABBf(bounds.minX, midY, bounds.minZ, midX, bounds.maxY, midZ), depth + 1);
            children[5] = new OctreeNode(new AABBf(midX, midY, bounds.minZ, bounds.maxX, bounds.maxY, midZ), depth + 1);
            children[6] = new OctreeNode(new AABBf(bounds.minX, midY, midZ, midX, bounds.maxY, bounds.maxZ), depth + 1);
            children[7] = new OctreeNode(new AABBf(midX, midY, midZ, bounds.maxX, bounds.maxY, bounds.maxZ), depth + 1);
        }

        int getChildIndex(AABBf objBounds) {
            if (children == null)
                return -1;

            float midX = (bounds.minX + bounds.maxX) / 2;
            float midY = (bounds.minY + bounds.maxY) / 2;
            float midZ = (bounds.minZ + bounds.maxZ) / 2;

            // Determine which octant the object belongs to
            boolean left = objBounds.maxX <= midX;
            boolean right = objBounds.minX >= midX;
            boolean bottom = objBounds.maxY <= midY;
            boolean top = objBounds.minY >= midY;
            boolean back = objBounds.maxZ <= midZ;
            boolean front = objBounds.minZ >= midZ;

            if (left && bottom && back)
                return 0;
            if (right && bottom && back)
                return 1;
            if (left && bottom && front)
                return 2;
            if (right && bottom && front)
                return 3;
            if (left && top && back)
                return 4;
            if (right && top && back)
                return 5;
            if (left && top && front)
                return 6;
            if (right && top && front)
                return 7;

            return -1; // Doesn't fit entirely in one child
        }

        void tryMerge() {
            if (children == null)
                return;

            // Check if we can merge children back
            int totalObjects = objects.size();
            for (OctreeNode child : children) {
                if (child != null) {
                    totalObjects += child.objects.size();
                    if (child.children != null) {
                        return; // Has grandchildren, don't merge
                    }
                }
            }

            if (totalObjects <= MAX_OBJECTS_PER_NODE) {
                // Merge children back
                for (OctreeNode child : children) {
                    if (child != null) {
                        objects.addAll(child.objects);
                    }
                }
                children = null;
            }
        }

        void collectVisible(FrustumIntersection frustum, List<Graphics> visible) {
            // Test this node's bounds against frustum
            if (!frustum.testAab(bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ)) {
                return; // Entire node culled
            }

            // Add objects in this node
            for (Graphics graphics : objects) {
                if (graphics.shouldRender()) {
                    visible.add(graphics);
                }
            }

            // Recurse to children
            if (children != null) {
                for (OctreeNode child : children) {
                    if (child != null) {
                        child.collectVisible(frustum, visible);
                    }
                }
            }
        }
    }
}