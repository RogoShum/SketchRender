package rogo.sketchrender.util;

import java.util.*;

public class IndexedQueue<T> {
    private final Map<T, Integer> objectToIndex = new HashMap<>();
    private final Map<Integer, T> indexToObject = new HashMap<>();
    private final PriorityQueue<Integer> freeIndices = new PriorityQueue<>();
    private int nextIndex = 0;

    public int add(T object) {
        if (objectToIndex.containsKey(object)) {
            return getIndex(object);
        }

        int index;
        if (!freeIndices.isEmpty()) {
            index = freeIndices.poll();
        } else {
            index = nextIndex++;
        }

        objectToIndex.put(object, index);
        indexToObject.put(index, object);
        return index;
    }

    public boolean remove(T object) {
        Integer index = objectToIndex.remove(object);
        if (index == null) {
            return false;
        }

        indexToObject.remove(index);
        freeIndices.add(index);
        return true;
    }

    public Integer getIndex(T object) {
        return objectToIndex.get(object);
    }

    public T getObject(int index) {
        return indexToObject.get(index);
    }

    public Collection<T> getAllObjects() {
        return Collections.unmodifiableCollection(objectToIndex.keySet());
    }

    public Collection<Integer> getAllIndices() {
        return Collections.unmodifiableCollection(indexToObject.keySet());
    }

    public int size() {
        return nextIndex;
    }
}
