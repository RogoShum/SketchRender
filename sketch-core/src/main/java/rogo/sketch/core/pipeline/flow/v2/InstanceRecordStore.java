package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class InstanceRecordStore<G extends Graphics> {
    private final List<InstanceRecord<G>> records = new ArrayList<>();
    private final List<Integer> generations = new ArrayList<>();
    private final ArrayDeque<Integer> freeSlots = new ArrayDeque<>();
    private final Map<G, InstanceHandle> byGraphics = new IdentityHashMap<>();

    public synchronized InstanceRecord<G> register(
            G graphics,
            KeyId stageId,
            PipelineType pipelineType,
            RenderParameter renderParameter,
            KeyId containerType) {
        if (graphics == null) {
            return null;
        }

        InstanceRecord<G> existing = get(graphics);
        if (existing != null) {
            return existing;
        }

        int slot = acquireSlot();
        int generation = generations.get(slot);
        InstanceHandle handle = new InstanceHandle(slot, generation);
        InstanceRecord<G> record = new InstanceRecord<>(handle, graphics, stageId, pipelineType, renderParameter, containerType);
        records.set(slot, record);
        byGraphics.put(graphics, handle);
        return record;
    }

    public synchronized InstanceRecord<G> get(G graphics) {
        InstanceHandle handle = byGraphics.get(graphics);
        return handle != null ? get(handle) : null;
    }

    public synchronized InstanceRecord<G> get(InstanceHandle handle) {
        if (!isValid(handle)) {
            return null;
        }
        return records.get(handle.slot());
    }

    public synchronized InstanceHandle handleOf(Graphics graphics) {
        @SuppressWarnings("unchecked")
        G typed = (G) graphics;
        return byGraphics.get(typed);
    }

    public synchronized InstanceRecord<G> remove(Graphics graphics) {
        InstanceHandle handle = byGraphics.remove(graphics);
        return handle != null ? remove(handle) : null;
    }

    public synchronized InstanceRecord<G> remove(InstanceHandle handle) {
        if (!isValid(handle)) {
            return null;
        }
        InstanceRecord<G> removed = records.get(handle.slot());
        records.set(handle.slot(), null);
        generations.set(handle.slot(), handle.generation() + 1);
        freeSlots.addLast(handle.slot());
        if (removed != null) {
            byGraphics.remove(removed.graphics());
        }
        return removed;
    }

    public synchronized List<InstanceRecord<G>> records() {
        List<InstanceRecord<G>> snapshot = new ArrayList<>(records.size());
        for (InstanceRecord<G> record : records) {
            if (record != null) {
                snapshot.add(record);
            }
        }
        return snapshot;
    }

    public synchronized int size() {
        return byGraphics.size();
    }

    public synchronized boolean isEmpty() {
        return byGraphics.isEmpty();
    }

    public synchronized void clear() {
        records.clear();
        generations.clear();
        freeSlots.clear();
        byGraphics.clear();
    }

    public synchronized boolean isValid(InstanceHandle handle) {
        if (handle == null || !handle.isValid()) {
            return false;
        }
        if (handle.slot() < 0 || handle.slot() >= records.size()) {
            return false;
        }
        return generations.get(handle.slot()) == handle.generation() && records.get(handle.slot()) != null;
    }

    private int acquireSlot() {
        if (!freeSlots.isEmpty()) {
            return freeSlots.removeFirst();
        }
        int slot = records.size();
        records.add(null);
        generations.add(0);
        return slot;
    }
}
