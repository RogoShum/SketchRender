package rogo.sketchrender.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.Collection;
import java.util.Collections;

public class IndexPool<T> {
    private final Object2IntMap<T> objectToIndex = new Object2IntOpenHashMap<>();
    private final Int2ObjectMap<T> indexToObject = new Int2ObjectOpenHashMap<>();
    private final IntArrayList freeIndices = new IntArrayList();
    private int nextIndex = 0;

    public IndexPool() {
        objectToIndex.defaultReturnValue(-1);
    }

    public void add(T object) {
        if (object == null) {
            throw new NullPointerException("Object cannot be null");
        }

        if (objectToIndex.containsKey(object)) {
            return;
        }

        int index;
        if (!freeIndices.isEmpty()) {
            index = freeIndices.removeInt(freeIndices.size() - 1);
        } else {
            index = nextIndex++;
        }

        objectToIndex.put(object, index);
        indexToObject.put(index, object);
    }

    public boolean remove(T object) {
        if (object == null) {
            return false;
        }

        int index = objectToIndex.removeInt(object);
        if (index == -1) {
            return false;
        }

        indexToObject.remove(index);
        freeIndices.add(index); // 将释放的索引加入复用池
        return true;
    }

    public int indexOf(T object) {
        if (object == null) {
            throw new NullPointerException("Object cannot be null");
        }

        int index = objectToIndex.getInt(object);
        if (index == -1) {
            throw new IllegalArgumentException("Object not found in pool");
        }
        return index;
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
        return objectToIndex.size();
    }

    public int getMaxIndex() {
        return nextIndex - 1;
    }

    public void clear() {
        objectToIndex.clear();
        indexToObject.clear();
        freeIndices.clear();
        nextIndex = 0;
    }

    public boolean contains(T object) {
        return object != null && objectToIndex.containsKey(object);
    }

    public boolean containsIndex(int index) {
        return indexToObject.containsKey(index);
    }

    public int getFreeIndexCount() {
        return freeIndices.size();
    }

    public IntArrayList getFreeIndices() {
        return new IntArrayList(freeIndices);
    }
}