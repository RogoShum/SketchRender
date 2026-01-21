package rogo.sketch.render.pipeline.container;

import org.joml.primitives.AABBf;
import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.feature.culling.aabb.AABBObject;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.util.KeyId;
import org.joml.FrustumIntersection;

import java.util.*;

/**
 * AABB tree-based graphics container with spatial partitioning and frustum
 * culling.
 * Uses JOML primitives for cross-platform compatibility.
 * Requires graphics instances to implement {@link AABBObject}.
 */
public class AABBTreeContainer<C extends RenderContext> implements GraphicsContainer<C> {
    private final Map<KeyId, AABBNode> instanceNodes = new LinkedHashMap<>();
    private AABBNode root;

    @Override
    public void add(Graphics graphics) {
        if (!(graphics instanceof AABBObject)) {
            throw new IllegalArgumentException(
                    "Graphics instance must implement AABBObject to be added to AABBTreeContainer: " +
                            graphics.getClass().getName());
        }

        AABBObject aabbObject = (AABBObject) graphics;
        AABBf bounds = aabbObject.getAABB();

        if (bounds == null) {
            throw new IllegalArgumentException(
                    "AABBObject.getAABB() returned null for instance: " + graphics.getIdentifier());
        }

        AABBNode node = new AABBNode(graphics, new AABBf(bounds));
        instanceNodes.put(graphics.getIdentifier(), node);

        if (root == null) {
            root = node;
        } else {
            insertNode(node);
        }
    }

    @Override
    public void remove(KeyId identifier) {
        AABBNode node = instanceNodes.remove(identifier);
        if (node != null) {
            removeNode(node);
        }
    }

    @Override
    public void tick(C context) {
        for (AABBNode node : instanceNodes.values()) {
            Graphics graphics = node.graphics;

            // Update AABB if needed
            if (graphics instanceof AABBObject) {
                AABBf newBounds = ((AABBObject) graphics).getAABB();
                if (newBounds != null && !boundsEqual(newBounds, node.bounds)) {
                    // AABB changed - rebuild node
                    removeNode(node);
                    node.bounds.set(newBounds);
                    insertNode(node);
                }
            }

            if (graphics.shouldTick()) {
                graphics.tick(context);
            }
        }
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
            // No frustum available - return all instances that should render
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

    /**
     * Recursively collect visible nodes using frustum culling.
     */
    private void collectVisibleNodes(AABBNode node, FrustumIntersection frustum, List<Graphics> visible) {
        // Test node bounds against frustum
        if (!frustum.testAab(node.bounds.minX, node.bounds.minY, node.bounds.minZ,
                node.bounds.maxX, node.bounds.maxY, node.bounds.maxZ)) {
            return; // Entire subtree culled
        }

        if (node.isLeaf()) {
            if (node.graphics.shouldRender()) {
                visible.add(node.graphics);
            }
        } else {
            if (node.left != null) {
                collectVisibleNodes(node.left, frustum, visible);
            }
            if (node.right != null) {
                collectVisibleNodes(node.right, frustum, visible);
            }
        }
    }

    @Override
    public void clear() {
        instanceNodes.clear();
        root = null;
    }

    private void insertNode(AABBNode node) {
        if (root == null) {
            root = node;
            return;
        }

        // Simple insertion: find best sibling and create parent
        AABBNode sibling = findBestSibling(root, node.bounds);
        AABBNode oldParent = sibling.parent;

        AABBNode newParent = new AABBNode(null, combine(sibling.bounds, node.bounds));
        newParent.parent = oldParent;

        if (oldParent != null) {
            if (oldParent.left == sibling) {
                oldParent.left = newParent;
            } else {
                oldParent.right = newParent;
            }
        } else {
            root = newParent;
        }

        newParent.left = sibling;
        newParent.right = node;
        sibling.parent = newParent;
        node.parent = newParent;

        // Refit bounds up the tree
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
            if (parent.parent.left == parent) {
                parent.parent.left = sibling;
            } else {
                parent.parent.right = sibling;
            }
            sibling.parent = parent.parent;
            refitBounds(parent.parent);
        } else {
            root = sibling;
            sibling.parent = null;
        }
    }

    private AABBNode findBestSibling(AABBNode node, AABBf bounds) {
        if (node.isLeaf()) {
            return node;
        }

        AABBf combinedLeft = combine(node.left.bounds, bounds);
        AABBf combinedRight = combine(node.right.bounds, bounds);

        double costLeft = getVolume(combinedLeft);
        double costRight = getVolume(combinedRight);

        if (costLeft < costRight) {
            return findBestSibling(node.left, bounds);
        } else {
            return findBestSibling(node.right, bounds);
        }
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
                Math.min(a.minX, b.minX),
                Math.min(a.minY, b.minY),
                Math.min(a.minZ, b.minZ),
                Math.max(a.maxX, b.maxX),
                Math.max(a.maxY, b.maxY),
                Math.max(a.maxZ, b.maxZ));
    }

    private double getVolume(AABBf aabb) {
        return (aabb.maxX - aabb.minX) *
                (aabb.maxY - aabb.minY) *
                (aabb.maxZ - aabb.minZ);
    }

    private boolean boundsEqual(AABBf a, AABBf b) {
        return a.minX == b.minX && a.minY == b.minY && a.minZ == b.minZ &&
                a.maxX == b.maxX && a.maxY == b.maxY && a.maxZ == b.maxZ;
    }

    private static class AABBNode {
        Graphics graphics; // null for internal nodes
        AABBf bounds;
        AABBNode parent;
        AABBNode left;
        AABBNode right;

        AABBNode(Graphics graphics, AABBf bounds) {
            this.graphics = graphics;
            this.bounds = bounds;
        }

        boolean isLeaf() {
            return graphics != null;
        }
    }
}
