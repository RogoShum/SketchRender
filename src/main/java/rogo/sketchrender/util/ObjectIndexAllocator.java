package rogo.sketchrender.util;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public class ObjectIndexAllocator<T> {
    private final Map<T, Integer> objectToIndex = new HashMap<>();
    private final ArrayDeque<Integer> freeIndices = new ArrayDeque<>();
    private int nextIndex = 0;

    public int allocate(T object) {
        Integer existingIndex = objectToIndex.get(object);
        if (existingIndex != null) {
            return existingIndex;
        }

        int newIndex = freeIndices.isEmpty() ? nextIndex++ : freeIndices.pollLast();
        objectToIndex.put(object, newIndex);
        return newIndex;
    }

    public void free(T object) {
        Integer index = objectToIndex.remove(object);
        if (index != null) {
            freeIndices.addLast(index);
        }
    }

    public Integer getIndex(T object) {
        return objectToIndex.get(object);
    }

    public boolean hasObject(T object) {
        return objectToIndex.containsKey(object);
    }
}