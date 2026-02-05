package rogo.sketch.core.util.transform;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Manages a shared pool of integer IDs.
 * Thread-safety: Not thread-safe. Assumes registration happens on Main Thread.
 */
public class SharedIdRegistry {
    private final IntArrayList freeIds = new IntArrayList();
    private int nextId = 0;

    public int allocate() {
        if (!freeIds.isEmpty()) {
            return freeIds.removeInt(freeIds.size() - 1);
        }
        return nextId++;
    }

    public void recycle(int id) {
        freeIds.add(id);
    }

    public int getMaxId() {
        return nextId;
    }

    public void clear() {
        freeIds.clear();
        nextId = 0;
    }
}