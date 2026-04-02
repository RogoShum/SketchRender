package rogo.sketch.core.pipeline.flow.v2;

import org.joml.FrustumIntersection;
import org.joml.primitives.AABBf;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.container.GraphicsContainer;
import rogo.sketch.core.pipeline.flow.container.DefaultBatchContainers;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class VisibilityContainerRegistry<C extends RenderContext> {
    private final InstanceRecordStore<? extends Graphics> recordStore;
    private final Map<KeyId, VisibilityIndex<C>> activeContainers = new LinkedHashMap<>();

    public VisibilityContainerRegistry(InstanceRecordStore<? extends Graphics> recordStore) {
        this.recordStore = recordStore;
    }

    public VisibilityIndex<C> getOrCreate(
            KeyId containerId,
            Supplier<? extends GraphicsContainer<? extends RenderContext>> supplier) {
        KeyId resolvedId = containerId != null ? containerId : DefaultBatchContainers.DEFAULT;
        VisibilityIndex<C> existing = activeContainers.get(resolvedId);
        if (existing != null) {
            return existing;
        }
        VisibilityIndex<C> created = createIndex(resolvedId, supplier);
        activeContainers.put(resolvedId, created);
        return created;
    }

    public void upsert(
            InstanceRecord<? extends Graphics> record,
            Supplier<? extends GraphicsContainer<? extends RenderContext>> supplier) {
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
        VisibilityIndex<C> index = getOrCreate(record.containerType(), supplier);
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
    private VisibilityIndex<C> createIndex(
            KeyId containerId,
            Supplier<? extends GraphicsContainer<? extends RenderContext>> supplier) {
        if (supplier != null) {
            GraphicsContainer<C> legacyContainer = (GraphicsContainer<C>) supplier.get();
            return new LegacyGraphicsContainerVisibilityIndex<>(containerId, legacyContainer, recordStore);
        }
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

    private static class AabbVisibilityIndex<C extends RenderContext> extends AbstractVisibilityIndex<C> {
        private AabbVisibilityIndex(KeyId containerType) {
            super(containerType);
        }

        @Override
        public void collectVisible(C context, Consumer<InstanceHandle> sink) {
            FrustumIntersection frustum = context != null ? context.getFrustum() : null;
            for (Map.Entry<InstanceHandle, VisibilityMetadata> entry : entries.entrySet()) {
                if (isVisible(frustum, entry.getValue())) {
                    sink.accept(entry.getKey());
                }
            }
        }

        protected boolean isVisible(FrustumIntersection frustum, VisibilityMetadata metadata) {
            if (metadata == null || metadata.bounds() == null || frustum == null) {
                return true;
            }
            AABBf bounds = metadata.bounds();
            return frustum.testAab(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
        }
    }

    private static final class OctreeVisibilityIndex<C extends RenderContext> extends AabbVisibilityIndex<C> {
        private OctreeVisibilityIndex(KeyId containerType) {
            super(containerType);
        }
    }

    private static final class LegacyGraphicsContainerVisibilityIndex<C extends RenderContext> implements VisibilityIndex<C> {
        private final KeyId containerType;
        private final GraphicsContainer<C> legacyContainer;
        private final InstanceRecordStore<? extends Graphics> recordStore;

        private LegacyGraphicsContainerVisibilityIndex(
                KeyId containerType,
                GraphicsContainer<C> legacyContainer,
                InstanceRecordStore<? extends Graphics> recordStore) {
            this.containerType = containerType;
            this.legacyContainer = legacyContainer;
            this.recordStore = recordStore;
        }

        @Override
        public KeyId containerType() {
            return containerType;
        }

        @Override
        public void insert(InstanceHandle handle, VisibilityMetadata metadata) {
            addOrUpdate(handle);
        }

        @Override
        public void remove(InstanceHandle handle) {
            InstanceRecord<? extends Graphics> record = recordStore.get(handle);
            if (record != null && record.graphics() != null) {
                legacyContainer.remove(record.graphics().getIdentifier());
            }
        }

        @Override
        public void update(InstanceHandle handle, VisibilityMetadata metadata) {
            addOrUpdate(handle);
        }

        @Override
        public void collectVisible(C context, Consumer<InstanceHandle> sink) {
            for (Graphics graphics : legacyContainer.getVisibleInstances(context)) {
                InstanceHandle handle = recordStore.handleOf(graphics);
                if (handle != null && handle.isValid()) {
                    sink.accept(handle);
                }
            }
        }

        @Override
        public boolean isEmpty() {
            return legacyContainer.isEmpty();
        }

        @Override
        public void clear() {
            legacyContainer.clear();
        }

        private void addOrUpdate(InstanceHandle handle) {
            InstanceRecord<? extends Graphics> record = recordStore.get(handle);
            if (record == null || record.graphics() == null) {
                return;
            }
            legacyContainer.remove(record.graphics().getIdentifier());
            legacyContainer.add(record.graphics(), record.renderParameter());
        }
    }
}
