package rogo.sketch.core.pipeline.container;

import org.joml.FrustumIntersection;
import org.joml.primitives.AABBf;
import rogo.sketch.core.api.graphics.AABBGraphics;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

import java.util.*;

public class OctreeContainer<C extends RenderContext> extends BaseGraphicsContainer<C> {
    public static final KeyId CONTAINER_TYPE = KeyId.of("octree");

    private static final int MAX_DEPTH = 8;
    private static final int MAX_OBJECTS_PER_NODE = 8;

    private final Map<KeyId, Graphics> instanceMap = new LinkedHashMap<>();
    private final Map<KeyId, AABBf> lastBounds = new HashMap<>();
    private OctreeNode root;

    public OctreeContainer() {
        root = new OctreeNode(new AABBf(-1000, -1000, -1000, 1000, 1000, 1000), 0);
    }

    // Additional internal constructor if needed, but not part of interface. User
    // used default.

    @Override
    protected boolean addImpl(Graphics graphics) {
        if (!(graphics instanceof AABBGraphics)) {
            throw new IllegalArgumentException(
                    "Graphics instance must implement AABBObject to be added to OctreeContainer");
        }
        AABBGraphics aabbGraphics = (AABBGraphics) graphics;
        AABBf bounds = aabbGraphics.getAABB();
        if (bounds == null) {
            throw new IllegalArgumentException("AABB is null");
        }

        instanceMap.put(graphics.getIdentifier(), graphics);
        lastBounds.put(graphics.getIdentifier(), new AABBf(bounds));
        root.insert(graphics, bounds);
        return true;
    }

    @Override
    protected Graphics removeImpl(KeyId identifier) {
        Graphics graphics = instanceMap.remove(identifier);
        if (graphics != null) {
            AABBf bounds = lastBounds.remove(identifier);
            if (bounds != null) {
                root.remove(graphics, bounds); // Does not merge automatically in my previous impl?
                root.tryMerge();
            }
        }
        return graphics;
    }

    @Override
    public void swapData() {
        super.swapData(); // Ticks asyncs swap and cleans up

        // Handle Octree updates safely here
        for (Graphics graphics : instanceMap.values()) {
            if (graphics instanceof AABBGraphics) {
                AABBGraphics aabbGraphics = (AABBGraphics) graphics;
                AABBf newBounds = aabbGraphics.getAABB();
                AABBf oldBounds = lastBounds.get(graphics.getIdentifier());
                if (newBounds != null && oldBounds != null && !boundsEqual(newBounds, oldBounds)) {
                    root.remove(graphics, oldBounds);
                    oldBounds.set(newBounds);
                    root.insert(graphics, newBounds);
                }
            }
        }
    }

    private boolean boundsEqual(AABBf a, AABBf b) {
        return a.minX == b.minX && a.minY == b.minY && a.minZ == b.minZ &&
                a.maxX == b.maxX && a.maxY == b.maxY && a.maxZ == b.maxZ;
    }

    @Override
    public Collection<Graphics> getAllInstances() {
        return new ArrayList<>(instanceMap.values());
    }

    @Override
    public Collection<Graphics> getVisibleInstances(C context) {
        FrustumIntersection frustum = context.getFrustum();
        if (frustum == null || root == null) {
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
        tickableInstances.clear();
        asyncTickableInstances.clear();
    }

    @Override
    public KeyId getContainerType() {
        return CONTAINER_TYPE;
    }

    // Octree Node Class (Same as before)
    private static class OctreeNode {
        AABBf bounds;
        int depth;
        List<Graphics> objects = new ArrayList<>();
        OctreeNode[] children = null;

        OctreeNode(AABBf bounds, int depth) {
            this.bounds = bounds;
            this.depth = depth;
        }

        void insert(Graphics graphics, AABBf graphicsBounds) {
            if (children != null) {
                int childIndex = getChildIndex(graphicsBounds);
                if (childIndex != -1) {
                    children[childIndex].insert(graphics, graphicsBounds);
                    return;
                }
            }
            objects.add(graphics);
            if (objects.size() > MAX_OBJECTS_PER_NODE && depth < MAX_DEPTH && children == null) {
                subdivide();
                List<Graphics> remaining = new ArrayList<>();
                for (Graphics obj : objects) {
                    if (obj instanceof AABBGraphics) {
                        AABBf objBounds = ((AABBGraphics) obj).getAABB();
                        if (objBounds != null) {
                            int childIndex = getChildIndex(objBounds);
                            if (childIndex != -1)
                                children[childIndex].insert(obj, objBounds);
                            else
                                remaining.add(obj);
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
                if (childIndex != -1)
                    children[childIndex].remove(graphics, graphicsBounds);
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
            return -1;
        }

        void tryMerge() {
            if (children == null)
                return;
            int total = objects.size();
            for (OctreeNode child : children) {
                if (child != null)
                    total += child.objects.size();
                if (child.children != null)
                    return;
            }
            if (total <= MAX_OBJECTS_PER_NODE) {
                for (OctreeNode child : children)
                    if (child != null)
                        objects.addAll(child.objects);
                children = null;
            }
        }

        void collectVisible(FrustumIntersection frustum, List<Graphics> visible) {
            if (!frustum.testAab(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ))
                return;
            for (Graphics g : objects)
                if (g.shouldRender())
                    visible.add(g);
            if (children != null)
                for (OctreeNode child : children)
                    if (child != null)
                        child.collectVisible(frustum, visible);
        }
    }
}