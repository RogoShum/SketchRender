package rogo.sketchrender.util;

import java.util.*;
import java.util.function.Consumer;

public class LifeTimer<T> {
    private final Map<T, Integer> usageTick;

    public LifeTimer() {
        usageTick = new HashMap<>();
    }

    public Set<Object> tick(int clientTick, int count) {
        Iterator<Map.Entry<T, Integer>> iterator = usageTick.entrySet().iterator();
        Set<Object> removed = new HashSet<>();

        while (iterator.hasNext()) {
            Map.Entry<T, Integer> entry = iterator.next();
            int tick = entry.getValue();
            if (clientTick - tick > count) {
                iterator.remove();
                removed.add(entry.getKey());
            }
        }

        return removed;
    }

    public void updateUsageTick(T hash, int tick) {
        usageTick.put(hash, tick);
    }

    public boolean contains(T hash) {
        return usageTick.containsKey(hash);
    }

    public void clear() {
        usageTick.clear();
    }

    public int size() {
        return usageTick.size();
    }

    public void foreach(Consumer<T> consumer) {
        usageTick.keySet().forEach(consumer);
    }

    public IndexedSet<T> toIndexedSet() {
        IndexedSet<T> set = new IndexedSet<>();
        usageTick.keySet().forEach(set::add);
        return set;
    }
}