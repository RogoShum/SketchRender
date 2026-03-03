package rogo.sketch.core.pipeline.flow.impl;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.container.GraphicsContainer;
import rogo.sketch.core.pipeline.flow.BatchContainer;
import rogo.sketch.core.pipeline.flow.RenderBatch;
import rogo.sketch.core.pipeline.flow.container.ContainerDescriptor;
import rogo.sketch.core.pipeline.flow.container.DefaultBatchContainers;
import rogo.sketch.core.pipeline.flow.dirty.DirtyEvent;
import rogo.sketch.core.pipeline.flow.dirty.DirtyQueue;
import rogo.sketch.core.pipeline.flow.dirty.DirtyReason;
import rogo.sketch.core.pipeline.flow.storage.BatchSoAStorage;
import rogo.sketch.core.pipeline.flow.storage.InstanceSlot;
import rogo.sketch.core.pipeline.information.InstanceInfo;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Shared merged core for flow-specific batch containers.
 */
public abstract class AbstractMergedBatchContainer<G extends Graphics, I extends InstanceInfo<G>, K>
        implements BatchContainer<G, I> {

    protected final Map<K, RenderBatch<I>> batches = new ConcurrentHashMap<>();
    protected final BatchSoAStorage<G, I> storage = new BatchSoAStorage<>();
    protected final DirtyQueue<G> dirtyQueue = new DirtyQueue<>();
    private volatile long visibilityRevision = 0L;

    private final Map<KeyId, ContainerDescriptor<RenderContext>> descriptors = new ConcurrentHashMap<>();
    private final Map<KeyId, GraphicsContainer<RenderContext>> activeContainers = new ConcurrentHashMap<>();
    private final List<ContainerListener> externalListeners = Collections.synchronizedList(new ArrayList<>());

    protected AbstractMergedBatchContainer() {
        registerContainerDescriptor(DefaultBatchContainers.QUEUE_DESCRIPTOR);
    }

    @Override
    public void registerContainerDescriptor(ContainerDescriptor<? extends RenderContext> descriptor) {
        @SuppressWarnings("unchecked")
        ContainerDescriptor<RenderContext> casted = (ContainerDescriptor<RenderContext>) descriptor;
        descriptors.put(casted.id(), casted);
    }

    @Override
    public void registerContainerListener(ContainerListener listener) {
        if (listener == null || externalListeners.contains(listener)) {
            return;
        }
        externalListeners.add(listener);
    }

    @Override
    public void unregisterContainerListener(ContainerListener listener) {
        externalListeners.remove(listener);
    }

    @Override
    public GraphicsContainer<? extends RenderContext> getGraphicsContainer(KeyId id) {
        return activeContainers.get(id);
    }

    @Override
    public GraphicsContainer<? extends RenderContext> getOrCreateGraphicsContainer(
            KeyId id,
            Supplier<? extends GraphicsContainer<? extends RenderContext>> supplier) {
        return getOrCreateContainerInternal(id, supplier);
    }

    @Override
    public Map<KeyId, GraphicsContainer<? extends RenderContext>> getActiveGraphicsContainers() {
        Map<KeyId, GraphicsContainer<? extends RenderContext>> view = new LinkedHashMap<>();
        for (Map.Entry<KeyId, GraphicsContainer<RenderContext>> entry : activeContainers.entrySet()) {
            view.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(view);
    }

    @Override
    public void registerInstance(G graphics, RenderParameter renderParameter) {
        addInstance(graphics, renderParameter, defaultContainerId(), null);
    }

    @Override
    public void addInstance(
            G graphics,
            RenderParameter renderParameter,
            KeyId containerId,
            Supplier<? extends GraphicsContainer<? extends RenderContext>> supplier) {
        if (graphics == null || renderParameter == null) {
            return;
        }
        if (!getGraphicsType().isInstance(graphics)) {
            throw new IllegalArgumentException("Unsupported graphics type: " + graphics.getClass().getName());
        }

        KeyId resolvedContainerId = containerId != null ? containerId : defaultContainerId();
        ContainerDescriptor<RenderContext> descriptor = descriptors.get(resolvedContainerId);

        if (descriptor == null && supplier == null) {
            descriptor = DefaultBatchContainers.find(resolvedContainerId).orElse(null);
            if (descriptor != null) {
                registerContainerDescriptor(descriptor);
            }
        }

        if (descriptor != null && !descriptor.supports(graphics, renderParameter)) {
            throw new IllegalArgumentException(
                    "Graphics type " + graphics.getClass().getName() + " is not supported by container " + resolvedContainerId);
        }

        InstanceSlot<G, I> slot = storage.get(graphics);
        boolean isNew = slot == null;
        if (slot != null) {
            removeFromContainerIndex(slot);
            removeFromBatch(slot);
        } else {
            slot = storage.getOrCreate(graphics);
        }

        GraphicsContainer<RenderContext> container = getOrCreateContainerInternal(resolvedContainerId, supplier);
        container.add(graphics, renderParameter);

        RenderSetting renderSetting = RenderSetting.fromPartial(renderParameter, graphics.getPartialRenderSetting());
        K batchKey = computeBatchKey(graphics, renderParameter, renderSetting);
        RenderBatch<I> batch = batches.computeIfAbsent(batchKey,
                key -> createRenderBatch(key, renderSetting, graphics, renderParameter));
        I info = createInstanceInfo(graphics, renderSetting, renderParameter);
        batch.addInstance(info);

        slot.setRenderParameter(renderParameter);
        slot.setContainerId(resolvedContainerId);
        slot.setBatchKey(batchKey);
        slot.setBatch(batch);
        slot.setInfo(info);

        if (isNew) {
            notifyExternalAdded(graphics, renderParameter, resolvedContainerId);
        }
    }

    @Override
    public void unregisterInstance(Graphics graphics) {
        removeInstance(graphics);
    }

    @Override
    public void removeInstance(Graphics graphics) {
        if (graphics == null) {
            return;
        }
        if (!getGraphicsType().isInstance(graphics)) {
            return;
        }

        @SuppressWarnings("unchecked")
        G casted = (G) graphics;
        InstanceSlot<G, I> slot = storage.get(casted);
        if (slot == null || slot.containerId() == null) {
            return;
        }

        GraphicsContainer<RenderContext> container = activeContainers.get(slot.containerId());
        if (container != null) {
            container.remove(graphics.getIdentifier());
        }
        removeFromBatch(slot);
        storage.remove(casted);
        notifyExternalRemoved(graphics);
        tryDestroyContainer(slot.containerId());
    }

    @Override
    public void handleDirtyInstance(G graphics) {
        InstanceSlot<G, I> slot = storage.get(graphics);
        if (slot == null || slot.renderParameter() == null) {
            return;
        }
        removeFromBatch(slot);

        RenderParameter renderParameter = slot.renderParameter();
        RenderSetting renderSetting = RenderSetting.fromPartial(renderParameter, graphics.getPartialRenderSetting());

        K batchKey = computeBatchKey(graphics, renderParameter, renderSetting);
        RenderBatch<I> batch = batches.computeIfAbsent(batchKey,
                key -> createRenderBatch(key, renderSetting, graphics, renderParameter));

        I info = createInstanceInfo(graphics, renderSetting, renderParameter);
        batch.addInstance(info);

        slot.setBatchKey(batchKey);
        slot.setBatch(batch);
        slot.setInfo(info);
    }

    @Override
    public Collection<RenderBatch<I>> getAllBatches() {
        return batches.values();
    }

    @Override
    public Collection<RenderBatch<I>> getActiveBatches() {
        List<RenderBatch<I>> active = new ArrayList<>();
        for (RenderBatch<I> batch : batches.values()) {
            if (!batch.isEmpty()) {
                active.add(batch);
            }
        }
        active.sort(Comparator
                .comparingLong((RenderBatch<I> batch) -> batch.getFirstVisibleOrderKey())
                .thenComparingInt(batch -> batch.getRenderSetting().hashCode()));
        return active;
    }

    @Override
    public void prepareForFrame() {
        collectDirtyFromSlots();
        Set<G> dedup = new LinkedHashSet<>();
        for (DirtyEvent<G> event : dirtyQueue.drain()) {
            dedup.add(event.instance());
        }
        for (G graphics : dedup) {
            handleDirtyInstance(graphics);
        }
        for (RenderBatch<I> batch : batches.values()) {
            batch.clearVisibleInstances();
        }
    }

    @Override
    public void prepareVisibility(RenderContext context) {
        Map<RenderBatch<I>, List<I>> visibleByBatch = new IdentityHashMap<>();
        Map<RenderBatch<I>, Set<Graphics>> seenByBatch = new IdentityHashMap<>();
        Map<RenderBatch<I>, Long> firstVisibleOrder = new IdentityHashMap<>();
        long order = 0L;
        long revision = ++visibilityRevision;

        for (Map.Entry<KeyId, GraphicsContainer<RenderContext>> entry : getOrderedActiveContainers()) {
            List<Graphics> orderedVisible = entry.getValue().getVisibleInstances(context);
            for (int i = 0; i < orderedVisible.size(); i++) {
                Graphics graphics = orderedVisible.get(i);
                if (!getGraphicsType().isInstance(graphics)) {
                    continue;
                }

                G typed = getGraphicsType().cast(graphics);
                InstanceSlot<G, I> slot = storage.get(typed);
                if (slot == null) {
                    continue;
                }

                RenderBatch<I> batch = slot.batch();
                I info = slot.info();
                if (batch == null || info == null) {
                    continue;
                }

                Set<Graphics> seen = seenByBatch.computeIfAbsent(batch,
                        ignored -> Collections.newSetFromMap(new IdentityHashMap<>()));
                if (seen.add(graphics)) {
                    visibleByBatch.computeIfAbsent(batch, ignored -> new ArrayList<>()).add(info);
                    firstVisibleOrder.putIfAbsent(batch, order++);
                }
            }
        }

        for (RenderBatch<I> batch : batches.values()) {
            List<I> visible = visibleByBatch.get(batch);
            batch.setVisibleInstances(visible != null ? visible : new ArrayList<>());
            batch.setVisibilityMeta(firstVisibleOrder.getOrDefault(batch, Long.MAX_VALUE), revision);
        }
    }

    @Override
    public void tick(RenderContext context) {
        for (GraphicsContainer<RenderContext> container : activeContainers.values()) {
            container.tick(context);
        }
    }

    @Override
    public void asyncTick(RenderContext context) {
        for (GraphicsContainer<RenderContext> container : activeContainers.values()) {
            container.asyncTick(context);
        }
    }

    @Override
    public void swapData() {
        for (GraphicsContainer<RenderContext> container : activeContainers.values()) {
            container.swapData();
        }
    }

    @Override
    public void clear() {
        for (GraphicsContainer<RenderContext> container : activeContainers.values()) {
            container.clear();
        }
        activeContainers.clear();
        batches.clear();
        storage.clear();
        dirtyQueue.clear();
    }

    protected KeyId defaultContainerId() {
        return DefaultBatchContainers.DEFAULT;
    }

    protected abstract K computeBatchKey(G graphics, RenderParameter renderParameter, RenderSetting renderSetting);

    protected abstract RenderBatch<I> createRenderBatch(
            K batchKey,
            RenderSetting renderSetting,
            G graphics,
            RenderParameter renderParameter);

    protected abstract I createInstanceInfo(G graphics, RenderSetting renderSetting, RenderParameter renderParameter);

    private GraphicsContainer<RenderContext> getOrCreateContainerInternal(
            KeyId id,
            Supplier<? extends GraphicsContainer<? extends RenderContext>> fallbackSupplier) {
        GraphicsContainer<RenderContext> existing = activeContainers.get(id);
        if (existing != null) {
            return existing;
        }

        ContainerDescriptor<RenderContext> descriptor = descriptors.get(id);
        GraphicsContainer<? extends RenderContext> created;
        if (descriptor != null) {
            created = descriptor.create();
        } else if (fallbackSupplier != null) {
            created = fallbackSupplier.get();
        } else {
            throw new IllegalArgumentException("Unknown container descriptor: " + id);
        }

        @SuppressWarnings("unchecked")
        GraphicsContainer<RenderContext> casted = (GraphicsContainer<RenderContext>) created;
        activeContainers.put(id, casted);
        return casted;
    }

    private List<Map.Entry<KeyId, GraphicsContainer<RenderContext>>> getOrderedActiveContainers() {
        List<Map.Entry<KeyId, GraphicsContainer<RenderContext>>> ordered = new ArrayList<>(activeContainers.entrySet());
        ordered.sort(Comparator
                .comparingInt((Map.Entry<KeyId, GraphicsContainer<RenderContext>> entry) -> DefaultBatchContainers.orderOf(entry.getKey()))
                .thenComparing(entry -> entry.getKey().toString()));
        return ordered;
    }

    private void removeFromBatch(InstanceSlot<G, I> slot) {
        RenderBatch<I> batch = slot.batch();
        I info = slot.info();
        if (batch == null || info == null) {
            return;
        }
        batch.removeInstance(info);
        if (batch.isEmpty()) {
            @SuppressWarnings("unchecked")
            K key = (K) slot.batchKey();
            batches.remove(key);
        }
    }

    private void removeFromContainerIndex(InstanceSlot<G, I> slot) {
        KeyId containerId = slot.containerId();
        if (containerId == null) {
            return;
        }
        GraphicsContainer<RenderContext> container = activeContainers.get(containerId);
        if (container != null) {
            container.remove(slot.graphics().getIdentifier());
            tryDestroyContainer(containerId);
        }
    }

    private void tryDestroyContainer(KeyId containerId) {
        if (containerId == null) {
            return;
        }
        GraphicsContainer<RenderContext> container = activeContainers.get(containerId);
        if (container != null && container.isEmpty()) {
            activeContainers.remove(containerId);
        }
    }

    private void collectDirtyFromSlots() {
        for (InstanceSlot<G, I> slot : storage.slots()) {
            Graphics graphics = slot.graphics();
            if (graphics != null && graphics.getBatchDirtyFlags() != DirtyReason.NOT) {
                dirtyQueue.offer(slot.graphics(), graphics.getBatchDirtyFlags());
                graphics.resetBatchDirtyFlags();
            }
        }
    }

    private void notifyExternalAdded(Graphics graphics, RenderParameter renderParameter, KeyId containerId) {
        for (ContainerListener listener : externalListeners) {
            listener.onInstanceAdded(graphics, renderParameter, containerId);
        }
    }

    private void notifyExternalRemoved(Graphics graphics) {
        for (ContainerListener listener : externalListeners) {
            listener.onInstanceRemoved(graphics);
        }
    }
}

