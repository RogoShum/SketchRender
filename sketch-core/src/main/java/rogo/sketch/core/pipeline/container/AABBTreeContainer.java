package rogo.sketch.core.pipeline.container;

import org.joml.FrustumIntersection;
import org.joml.primitives.AABBf;
import rogo.sketch.core.api.graphics.AABBGraphics;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

import java.util.*;

public class AABBTreeContainer<C extends RenderContext> extends BaseGraphicsContainer<C> {
    public static final KeyId CONTAINER_TYPE = KeyId.of("aabb_tree");

    private final Map<KeyId, AABBNode> instanceNodes = new LinkedHashMap<>();
    private AABBNode root;

    @Override
    protected boolean addImpl(Graphics graphics) {
        if (!(graphics instanceof AABBGraphics)) {
            throw new IllegalArgumentException(
                    "Graphics instance must implement AABBObject to be added to AABBTreeContainer");
        }
        AABBGraphics aabbGraphics = (AABBGraphics) graphics;
        AABBf bounds = aabbGraphics.getAABB();
        if (bounds == null) {
            throw new IllegalArgumentException("AABB is null");
        }

        AABBNode node = new AABBNode(graphics, new AABBf(bounds));
        instanceNodes.put(graphics.getIdentifier(), node);
        if (root == null) {
            root = node;
        } else {
            insertNode(node);
        }
        return true;
    }

    @Override
    protected Graphics removeImpl(KeyId identifier) {
        AABBNode node = instanceNodes.remove(identifier);
        if (node != null) {
            removeNode(node);
            return node.graphics;
        }
        return null;
    }

    @Override
    public void asyncTick(C context) {
        super.asyncTick(context); // Ticks instances

        // Update AABBs for moving objects
        // CAUTION: Thread Safety. If this runs on worker pool, accessing
        // 'instanceNodes' (LinkedHashMap) and modifying tree is unsafe if Render thread
        // reads!
        // User Requirement: "Async Execution ... strict no modification of render data"
        // AABB Tree structure IS render data (used for culling).
        // WE CANNOT MODIFY TREE IN ASYNC TICK.
        // We should defer tree updates to Safe Sync Point (swapData or sync tick).
        // OR we use a lock. But strictly: "Zero-Allocation", "Parallel".
        // Better to check for updates in swapData()?
        // But AABB update calculation might reflect next frame position.
        // Ideally: Calculate new AABB in specific "Next" state, and update tree in
        // "Swap".

    }

    @Override
    public void swapData() {
        super.swapData(); // Calls swapData on asyncTickables and cleanupDiscarded()

        // Handle Tree Updates here?
        // Iterate checks for AABB changes.
        // Since we are single-threaded here (Pre-Tick), we can modify tree safely.

        for (AABBNode node : instanceNodes.values()) {
            Graphics graphics = node.graphics;
            if (graphics instanceof AABBGraphics) {
                AABBf newBounds = ((AABBGraphics) graphics).getAABB();
                if (newBounds != null && !boundsEqual(newBounds, node.bounds)) {
                    // Rebuild node
                    removeNode(node);
                    node.bounds.set(newBounds);
                    insertNode(node);
                }
            }
        }
    }

    // Helper to avoid duplicate code, logic from original class
    private boolean boundsEqual(AABBf a, AABBf b) {
        return a.minX == b.minX && a.minY == b.minY && a.minZ == b.minZ &&
                a.maxX == b.maxX && a.maxY == b.maxY && a.maxZ == b.maxZ;
    }

    @Override
    public Collection<Graphics> getAllInstances() {
        List<Graphics> all = new ArrayList<>(instanceNodes.size());
        for (AABBNode node : instanceNodes.values()) {
            all.add(node.graphics);
        }
        return all;
    }

    @Override
    public Collection<Graphics> getVisibleInstances(C context) {
        FrustumIntersection frustum = context.getFrustum();
        if (frustum == null || root == null) {
            List<Graphics> all = new ArrayList<>();
            for (AABBNode node : instanceNodes.values()) {
                if (node.graphics.shouldRender()) {
                    all.add(node.graphics);
                }
            }
            return all;
        }

        List<Graphics> visible = new ArrayList<>();
        collectVisibleNodes(root, frustum, visible);
        return visible;
    }

    private void collectVisibleNodes(AABBNode node, FrustumIntersection frustum, List<Graphics> visible) {
        if (!frustum.testAab(node.bounds.minX, node.bounds.minY, node.bounds.minZ,
                node.bounds.maxX, node.bounds.maxY, node.bounds.maxZ)) {
            return;
        }
        if (node.isLeaf()) {
            if (node.graphics.shouldRender()) {
                visible.add(node.graphics);
            }
        } else {
            if (node.left != null)
                collectVisibleNodes(node.left, frustum, visible);
            if (node.right != null)
                collectVisibleNodes(node.right, frustum, visible);
        }
    }

    @Override
    public void clear() {
        instanceNodes.clear();
        root = null;
        tickableInstances.clear();
        asyncTickableInstances.clear();
    }

    @Override
    public KeyId getContainerType() {
        return CONTAINER_TYPE;
    }

    // Tree Logic (Same as before)
    private void insertNode(AABBNode node) {
        if (root == null) {
            root = node;
            return;
        }
        AABBNode sibling = findBestSibling(root, node.bounds);
        AABBNode oldParent = sibling.parent;
        AABBNode newParent = new AABBNode(null, combine(sibling.bounds, node.bounds));
        newParent.parent = oldParent;
        if (oldParent != null) {
            if (oldParent.left == sibling)
                oldParent.left = newParent;
            else
                oldParent.right = newParent;
        } else {
            root = newParent;
        }
        newParent.left = sibling;
        newParent.right = node;
        sibling.parent = newParent;
        node.parent = newParent;
        refitBounds(newParent);
    }

    private void removeNode(AABBNode node) {
        if (node == root) {
            root = null;
            return;
        }
        AABBNode parent = node.parent;
        if (parent == null)
            return;
        AABBNode sibling = (parent.left == node) ? parent.right : parent.left;
        if (parent.parent != null) {
            if (parent.parent.left == parent)
                parent.parent.left = sibling;
            else
                parent.parent.right = sibling;
            sibling.parent = parent.parent;
            refitBounds(parent.parent);
        } else {
            root = sibling;
            sibling.parent = null;
        }
    }

    private AABBNode findBestSibling(AABBNode node, AABBf bounds) {
        if (node.isLeaf())
            return node;
        AABBf combinedLeft = combine(node.left.bounds, bounds);
        AABBf combinedRight = combine(node.right.bounds, bounds);
        if (getVolume(combinedLeft) < getVolume(combinedRight))
            return findBestSibling(node.left, bounds);
        else
            return findBestSibling(node.right, bounds);
    }

    private void refitBounds(AABBNode node) {
        while (node != null) {
            if (!node.isLeaf()) {
                node.bounds = combine(node.left.bounds, node.right.bounds);
            }
            node = node.parent;
        }
    }

    private AABBf combine(AABBf a, AABBf b) {
        return new AABBf(
                Math.min(a.minX, b.minX), Math.min(a.minY, b.minY), Math.min(a.minZ, b.minZ),
                Math.max(a.maxX, b.maxX), Math.max(a.maxY, b.maxY), Math.max(a.maxZ, b.maxZ));
    }

    private double getVolume(AABBf aabb) {
        return (aabb.maxX - aabb.minX) * (aabb.maxY - aabb.minY) * (aabb.maxZ - aabb.minZ);
    }

    private static class AABBNode {
        Graphics graphics;
        AABBf bounds;
        AABBNode parent, left, right;

        AABBNode(Graphics graphics, AABBf bounds) {
            this.graphics = graphics;
            this.bounds = bounds;
        }

        boolean isLeaf() {
            return graphics != null;
        }
    }
}
