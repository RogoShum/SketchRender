package rogo.sketch.core.pipeline.flow.v2;

import org.joml.FrustumIntersection;
import org.joml.primitives.AABBf;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.flow.container.DefaultBatchContainers;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class VisibilityContainerRegistry<C extends RenderContext> {
    private final Map<KeyId, VisibilityIndex<C>> activeContainers = new LinkedHashMap<>();

    public VisibilityContainerRegistry(InstanceRecordStore<? extends Graphics> ignoredRecordStore) {
    }

    public VisibilityIndex<C> getOrCreate(KeyId containerId) {
        KeyId resolvedId = containerId != null ? containerId : DefaultBatchContainers.DEFAULT;
        VisibilityIndex<C> existing = activeContainers.get(resolvedId);
        if (existing != null) {
            return existing;
        }
        VisibilityIndex<C> created = createIndex(resolvedId);
        activeContainers.put(resolvedId, created);
        return created;
    }

    public void upsert(InstanceRecord<? extends Graphics> record) {
        if (record == null || record.handle() == null || !record.handle().isValid()) {
            return;
        }
        KeyId previousContainer = record.visibilityContainerHandle();
        KeyId requestedContainer = record.containerType();
        if (previousContainer != null && !Objects.equals(previousContainer, requestedContainer)) {
            VisibilityIndex<C> previousIndex = activeContainers.get(previousContainer);
            if (previousIndex != null) {
                previousIndex.remove(record.handle());
                if (previousIndex.isEmpty()) {
                    activeContainers.remove(previousContainer);
                }
            }
        }
        VisibilityIndex<C> index = getOrCreate(record.containerType());
        record.setVisibilityContainerHandle(index.containerType());
        index.update(record.handle(), record.visibilityMetadata());
    }

    public void remove(InstanceRecord<? extends Graphics> record) {
        if (record == null) {
            return;
        }
        VisibilityIndex<C> index = activeContainers.get(record.visibilityContainerHandle());
        if (index != null) {
            index.remove(record.handle());
            if (index.isEmpty()) {
                activeContainers.remove(record.visibilityContainerHandle());
            }
        }
    }

    public List<Map.Entry<KeyId, VisibilityIndex<C>>> orderedEntries() {
        List<Map.Entry<KeyId, VisibilityIndex<C>>> ordered = new ArrayList<>(activeContainers.entrySet());
        ordered.sort(Comparator
                .comparingInt((Map.Entry<KeyId, VisibilityIndex<C>> entry) -> DefaultBatchContainers.orderOf(entry.getKey()))
                .thenComparing(entry -> entry.getKey().toString()));
        return ordered;
    }

    public void clear() {
        for (VisibilityIndex<C> index : activeContainers.values()) {
            index.clear();
        }
        activeContainers.clear();
    }

    @SuppressWarnings("unchecked")
    private VisibilityIndex<C> createIndex(KeyId containerId) {
        if (DefaultBatchContainers.PRIORITY.equals(containerId)) {
            return new PriorityVisibilityIndex<>(containerId);
        }
        if (DefaultBatchContainers.AABB_TREE.equals(containerId)) {
            return new AabbVisibilityIndex<>(containerId);
        }
        if (DefaultBatchContainers.OCTREE.equals(containerId)) {
            return new OctreeVisibilityIndex<>(containerId);
        }
        return new QueueVisibilityIndex<>(containerId);
    }

    private abstract static class AbstractVisibilityIndex<C extends RenderContext> implements VisibilityIndex<C> {
        private final KeyId containerType;
        protected final Map<InstanceHandle, VisibilityMetadata> entries = new LinkedHashMap<>();

        protected AbstractVisibilityIndex(KeyId containerType) {
            this.containerType = containerType;
        }

        @Override
        public KeyId containerType() {
            return containerType;
        }

        @Override
        public void insert(InstanceHandle handle, VisibilityMetadata metadata) {
            entries.put(handle, metadata);
        }

        @Override
        public void remove(InstanceHandle handle) {
            entries.remove(handle);
        }

        @Override
        public void update(InstanceHandle handle, VisibilityMetadata metadata) {
            entries.put(handle, metadata);
        }

        @Override
        public boolean isEmpty() {
            return entries.isEmpty();
        }

        @Override
        public void clear() {
            entries.clear();
        }
    }

    private static final class QueueVisibilityIndex<C extends RenderContext> extends AbstractVisibilityIndex<C> {
        private QueueVisibilityIndex(KeyId containerType) {
            super(containerType);
        }

        @Override
        public void collectVisible(C context, Consumer<InstanceHandle> sink) {
            for (InstanceHandle handle : entries.keySet()) {
                sink.accept(handle);
            }
        }
    }

    private static final class PriorityVisibilityIndex<C extends RenderContext> extends AbstractVisibilityIndex<C> {
        private PriorityVisibilityIndex(KeyId containerType) {
            super(containerType);
        }

        @Override
        public void collectVisible(C context, Consumer<InstanceHandle> sink) {
            List<Map.Entry<InstanceHandle, VisibilityMetadata>> ordered = new ArrayList<>(entries.entrySet());
            ordered.sort((left, right) -> {
                int compare = compareSortKey(left.getValue(), right.getValue());
                if (compare != 0) {
                    return compare;
                }
                return Long.compare(left.getValue().orderHint(), right.getValue().orderHint());
            });
            for (Map.Entry<InstanceHandle, VisibilityMetadata> entry : ordered) {
                sink.accept(entry.getKey());
            }
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private int compareSortKey(VisibilityMetadata left, VisibilityMetadata right) {
            Object leftKey = left != null ? left.sortKey() : null;
            Object rightKey = right != null ? right.sortKey() : null;
            if (Objects.equals(leftKey, rightKey)) {
                return 0;
            }
            if (leftKey instanceof Comparable comparable && rightKey != null) {
                try {
                    return comparable.compareTo(rightKey);
                } catch (ClassCastException ignored) {
                }
            }
            if (leftKey instanceof Number leftNumber && rightKey instanceof Number rightNumber) {
                return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
            }
            return String.valueOf(leftKey).compareTo(String.valueOf(rightKey));
        }
    }

    private static final class AabbVisibilityIndex<C extends RenderContext> implements VisibilityIndex<C> {
        private final KeyId containerType;
        private final Map<InstanceHandle, VisibilityMetadata> metadataByHandle = new LinkedHashMap<>();
        private final Map<InstanceHandle, AabbNode> leaves = new LinkedHashMap<>();
        private AabbNode root;

        private AabbVisibilityIndex(KeyId containerType) {
            this.containerType = containerType;
        }

        @Override
        public KeyId containerType() {
            return containerType;
        }

        @Override
        public void insert(InstanceHandle handle, VisibilityMetadata metadata) {
            update(handle, metadata);
        }

        @Override
        public void remove(InstanceHandle handle) {
            metadataByHandle.remove(handle);
            AabbNode node = leaves.remove(handle);
            if (node != null) {
                removeNode(node);
            }
        }

        @Override
        public void update(InstanceHandle handle, VisibilityMetadata metadata) {
            if (handle == null || !handle.isValid()) {
                return;
            }
            metadataByHandle.put(handle, metadata);
            AABBf bounds = metadata != null && metadata.bounds() != null ? new AABBf(metadata.bounds()) : null;
            AabbNode existing = leaves.get(handle);
            if (bounds == null) {
                if (existing != null) {
                    removeNode(existing);
                    leaves.remove(handle);
                }
                return;
            }
            if (existing == null) {
                AabbNode created = new AabbNode(handle, bounds);
                leaves.put(handle, created);
                insertNode(created);
                return;
            }
            if (!boundsEqual(existing.bounds, bounds)) {
                removeNode(existing);
                existing.bounds = bounds;
                insertNode(existing);
            }
        }

        @Override
        public void collectVisible(C context, Consumer<InstanceHandle> sink) {
            FrustumIntersection frustum = context != null ? context.getFrustum() : null;
            for (Map.Entry<InstanceHandle, VisibilityMetadata> entry : metadataByHandle.entrySet()) {
                if (!leaves.containsKey(entry.getKey()) && isVisible(frustum, entry.getValue())) {
                    sink.accept(entry.getKey());
                }
            }
            if (root == null) {
                return;
            }
            collectVisible(root, frustum, sink);
        }

        @Override
        public boolean isEmpty() {
            return metadataByHandle.isEmpty();
        }

        @Override
        public void clear() {
            metadataByHandle.clear();
            leaves.clear();
            root = null;
        }

        private void collectVisible(AabbNode node, FrustumIntersection frustum, Consumer<InstanceHandle> sink) {
            if (node == null) {
                return;
            }
            if (frustum != null && !frustum.testAab(
                    node.bounds.minX,
                    node.bounds.minY,
                    node.bounds.minZ,
                    node.bounds.maxX,
                    node.bounds.maxY,
                    node.bounds.maxZ)) {
                return;
            }
            if (node.isLeaf()) {
                sink.accept(node.handle);
                return;
            }
            collectVisible(node.left, frustum, sink);
            collectVisible(node.right, frustum, sink);
        }

        private boolean isVisible(FrustumIntersection frustum, VisibilityMetadata metadata) {
            if (metadata == null || metadata.bounds() == null || frustum == null) {
                return true;
            }
            AABBf bounds = metadata.bounds();
            return frustum.testAab(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
        }

        private void insertNode(AabbNode node) {
            if (root == null) {
                root = node;
                node.parent = null;
                return;
            }
            AabbNode sibling = findBestSibling(root, node.bounds);
            AabbNode oldParent = sibling.parent;
            AabbNode newParent = new AabbNode(null, combine(sibling.bounds, node.bounds));
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
            refitBounds(newParent);
        }

        private void removeNode(AabbNode node) {
            if (node == root) {
                root = null;
                return;
            }
            AabbNode parent = node.parent;
            if (parent == null) {
                return;
            }
            AabbNode sibling = parent.left == node ? parent.right : parent.left;
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
            node.parent = null;
        }

        private AabbNode findBestSibling(AabbNode node, AABBf bounds) {
            if (node.isLeaf()) {
                return node;
            }
            AABBf combinedLeft = combine(node.left.bounds, bounds);
            AABBf combinedRight = combine(node.right.bounds, bounds);
            if (volume(combinedLeft) <= volume(combinedRight)) {
                return findBestSibling(node.left, bounds);
            }
            return findBestSibling(node.right, bounds);
        }

        private void refitBounds(AabbNode node) {
            AabbNode current = node;
            while (current != null) {
                if (!current.isLeaf()) {
                    current.bounds = combine(current.left.bounds, current.right.bounds);
                }
                current = current.parent;
            }
        }

        private AABBf combine(AABBf left, AABBf right) {
            return new AABBf(
                    Math.min(left.minX, right.minX),
                    Math.min(left.minY, right.minY),
                    Math.min(left.minZ, right.minZ),
                    Math.max(left.maxX, right.maxX),
                    Math.max(left.maxY, right.maxY),
                    Math.max(left.maxZ, right.maxZ));
        }

        private double volume(AABBf bounds) {
            return (bounds.maxX - bounds.minX) * (bounds.maxY - bounds.minY) * (bounds.maxZ - bounds.minZ);
        }

        private boolean boundsEqual(AABBf left, AABBf right) {
            return left.minX == right.minX
                    && left.minY == right.minY
                    && left.minZ == right.minZ
                    && left.maxX == right.maxX
                    && left.maxY == right.maxY
                    && left.maxZ == right.maxZ;
        }
    }

    private static final class OctreeVisibilityIndex<C extends RenderContext> implements VisibilityIndex<C> {
        private static final int MAX_DEPTH = 8;
        private static final int MAX_OBJECTS_PER_NODE = 8;

        private final KeyId containerType;
        private final Map<InstanceHandle, VisibilityMetadata> metadataByHandle = new LinkedHashMap<>();
        private OctreeNode root;

        private OctreeVisibilityIndex(KeyId containerType) {
            this.containerType = containerType;
            this.root = new OctreeNode(new AABBf(-1000f, -1000f, -1000f, 1000f, 1000f, 1000f), 0);
        }

        @Override
        public KeyId containerType() {
            return containerType;
        }

        @Override
        public void insert(InstanceHandle handle, VisibilityMetadata metadata) {
            update(handle, metadata);
        }

        @Override
        public void remove(InstanceHandle handle) {
            metadataByHandle.remove(handle);
            rebuild();
        }

        @Override
        public void update(InstanceHandle handle, VisibilityMetadata metadata) {
            if (handle == null || !handle.isValid()) {
                return;
            }
            metadataByHandle.put(handle, metadata);
            rebuild();
        }

        @Override
        public void collectVisible(C context, Consumer<InstanceHandle> sink) {
            FrustumIntersection frustum = context != null ? context.getFrustum() : null;
            if (root == null) {
                return;
            }
            root.collectVisible(frustum, sink);
            for (Map.Entry<InstanceHandle, VisibilityMetadata> entry : metadataByHandle.entrySet()) {
                if (entry.getValue() == null || entry.getValue().bounds() == null) {
                    sink.accept(entry.getKey());
                }
            }
        }

        @Override
        public boolean isEmpty() {
            return metadataByHandle.isEmpty();
        }

        @Override
        public void clear() {
            metadataByHandle.clear();
            root = new OctreeNode(new AABBf(-1000f, -1000f, -1000f, 1000f, 1000f, 1000f), 0);
        }

        private void rebuild() {
            AABBf aggregate = null;
            for (VisibilityMetadata metadata : metadataByHandle.values()) {
                if (metadata == null || metadata.bounds() == null) {
                    continue;
                }
                aggregate = aggregate == null
                        ? new AABBf(metadata.bounds())
                        : expand(aggregate, metadata.bounds());
            }
            if (aggregate == null) {
                root = new OctreeNode(new AABBf(-1000f, -1000f, -1000f, 1000f, 1000f, 1000f), 0);
                return;
            }
            root = new OctreeNode(expandToCube(aggregate), 0);
            for (Map.Entry<InstanceHandle, VisibilityMetadata> entry : metadataByHandle.entrySet()) {
                if (entry.getValue() == null || entry.getValue().bounds() == null) {
                    continue;
                }
                root.insert(entry.getKey(), entry.getValue().bounds());
            }
        }

        private AABBf expand(AABBf base, AABBf next) {
            return new AABBf(
                    Math.min(base.minX, next.minX),
                    Math.min(base.minY, next.minY),
                    Math.min(base.minZ, next.minZ),
                    Math.max(base.maxX, next.maxX),
                    Math.max(base.maxY, next.maxY),
                    Math.max(base.maxZ, next.maxZ));
        }

        private AABBf expandToCube(AABBf bounds) {
            float sizeX = bounds.maxX - bounds.minX;
            float sizeY = bounds.maxY - bounds.minY;
            float sizeZ = bounds.maxZ - bounds.minZ;
            float size = Math.max(sizeX, Math.max(sizeY, sizeZ));
            float centerX = (bounds.minX + bounds.maxX) * 0.5f;
            float centerY = (bounds.minY + bounds.maxY) * 0.5f;
            float centerZ = (bounds.minZ + bounds.maxZ) * 0.5f;
            float half = Math.max(size * 0.5f, 1.0f);
            return new AABBf(
                    centerX - half,
                    centerY - half,
                    centerZ - half,
                    centerX + half,
                    centerY + half,
                    centerZ + half);
        }

        private static final class OctreeNode {
            private final AABBf bounds;
            private final int depth;
            private final List<Entry> objects = new ArrayList<>();
            private OctreeNode[] children;

            private OctreeNode(AABBf bounds, int depth) {
                this.bounds = bounds;
                this.depth = depth;
            }

            private void insert(InstanceHandle handle, AABBf entryBounds) {
                if (children != null) {
                    int childIndex = childIndex(entryBounds);
                    if (childIndex >= 0) {
                        children[childIndex].insert(handle, entryBounds);
                        return;
                    }
                }
                objects.add(new Entry(handle, new AABBf(entryBounds)));
                if (objects.size() > MAX_OBJECTS_PER_NODE && depth < MAX_DEPTH && children == null) {
                    subdivide();
                    redistribute();
                }
            }

            private void collectVisible(FrustumIntersection frustum, Consumer<InstanceHandle> sink) {
                if (frustum != null && !frustum.testAab(
                        bounds.minX,
                        bounds.minY,
                        bounds.minZ,
                        bounds.maxX,
                        bounds.maxY,
                        bounds.maxZ)) {
                    return;
                }
                for (Entry entry : objects) {
                    if (frustum == null || frustum.testAab(
                            entry.bounds.minX,
                            entry.bounds.minY,
                            entry.bounds.minZ,
                            entry.bounds.maxX,
                            entry.bounds.maxY,
                            entry.bounds.maxZ)) {
                        sink.accept(entry.handle);
                    }
                }
                if (children == null) {
                    return;
                }
                for (OctreeNode child : children) {
                    if (child != null) {
                        child.collectVisible(frustum, sink);
                    }
                }
            }

            private void redistribute() {
                List<Entry> remaining = new ArrayList<>();
                for (Entry entry : objects) {
                    int childIndex = childIndex(entry.bounds);
                    if (childIndex >= 0) {
                        children[childIndex].insert(entry.handle, entry.bounds);
                    } else {
                        remaining.add(entry);
                    }
                }
                objects.clear();
                objects.addAll(remaining);
            }

            private void subdivide() {
                float midX = (bounds.minX + bounds.maxX) * 0.5f;
                float midY = (bounds.minY + bounds.maxY) * 0.5f;
                float midZ = (bounds.minZ + bounds.maxZ) * 0.5f;
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

            private int childIndex(AABBf entryBounds) {
                if (children == null) {
                    return -1;
                }
                float midX = (bounds.minX + bounds.maxX) * 0.5f;
                float midY = (bounds.minY + bounds.maxY) * 0.5f;
                float midZ = (bounds.minZ + bounds.maxZ) * 0.5f;
                boolean left = entryBounds.maxX <= midX;
                boolean right = entryBounds.minX >= midX;
                boolean bottom = entryBounds.maxY <= midY;
                boolean top = entryBounds.minY >= midY;
                boolean back = entryBounds.maxZ <= midZ;
                boolean front = entryBounds.minZ >= midZ;
                if (left && bottom && back) return 0;
                if (right && bottom && back) return 1;
                if (left && bottom && front) return 2;
                if (right && bottom && front) return 3;
                if (left && top && back) return 4;
                if (right && top && back) return 5;
                if (left && top && front) return 6;
                if (right && top && front) return 7;
                return -1;
            }

            private record Entry(InstanceHandle handle, AABBf bounds) {
            }
        }
    }

    private static final class AabbNode {
        private final InstanceHandle handle;
        private AABBf bounds;
        private AabbNode parent;
        private AabbNode left;
        private AabbNode right;

        private AabbNode(InstanceHandle handle, AABBf bounds) {
            this.handle = handle;
            this.bounds = bounds;
        }

        private boolean isLeaf() {
            return handle != null;
        }
    }

}

