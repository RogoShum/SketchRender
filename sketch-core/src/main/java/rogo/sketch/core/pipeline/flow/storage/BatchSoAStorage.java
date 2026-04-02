package rogo.sketch.core.pipeline.flow.storage;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.information.InstanceInfo;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy dense slot storage with stable lookup by graphics reference.
 * <p>
 * New V2 instance management prefers {@code InstanceRecordStore}; this type is
 * retained only for legacy batch container compatibility.
 * </p>
 */
@Deprecated(forRemoval = false)
public class BatchSoAStorage<G extends Graphics, I extends InstanceInfo<G>> {
    private final List<InstanceSlot<G, I>> slots = new ArrayList<>();
    private final Map<G, InstanceSlot<G, I>> byGraphics = new IdentityHashMap<>();

    public synchronized InstanceSlot<G, I> get(G graphics) {
        return byGraphics.get(graphics);
    }

    public synchronized InstanceSlot<G, I> getOrCreate(G graphics) {
        InstanceSlot<G, I> slot = byGraphics.get(graphics);
        if (slot != null) {
            return slot;
        }

        InstanceSlot<G, I> created = new InstanceSlot<>(slots.size(), graphics);
        slots.add(created);
        byGraphics.put(graphics, created);
        return created;
    }

    public synchronized InstanceSlot<G, I> remove(G graphics) {
        InstanceSlot<G, I> removed = byGraphics.remove(graphics);
        if (removed == null) {
            return null;
        }

        int index = removed.index();
        int lastIndex = slots.size() - 1;
        if (index == lastIndex) {
            slots.remove(lastIndex);
            return removed;
        }

        InstanceSlot<G, I> last = slots.get(lastIndex);
        slots.set(index, last);
        last.setIndex(index);
        slots.remove(lastIndex);
        return removed;
    }

    public synchronized List<InstanceSlot<G, I>> slots() {
        return new ArrayList<>(slots);
    }

    public synchronized int size() {
        return slots.size();
    }

    public synchronized boolean isEmpty() {
        return slots.isEmpty();
    }

    public synchronized void clear() {
        slots.clear();
        byGraphics.clear();
    }
}

