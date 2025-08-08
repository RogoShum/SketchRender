package rogo.sketch.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.BiConsumer;

public class IndexedSet<E> {
    private final ArrayList<E> list;
    private final HashMap<E, Integer> elementToIndex;

    public IndexedSet() {
        list = new ArrayList<>();
        elementToIndex = new HashMap<>();
    }

    public boolean add(E element) {
        if (elementToIndex.containsKey(element)) return false;
        elementToIndex.put(element, list.size());
        list.add(element);
        return true;
    }

    public E get(int index) {
        return list.get(index);
    }

    public int indexOf(E element) {
        return elementToIndex.getOrDefault(element, -1);
    }

    public boolean remove(E element) {
        Integer index = elementToIndex.get(element);
        if (index == null) return false;
        list.remove(element);
        elementToIndex.remove(element);
        for (int i = index; i < list.size(); ++i) {
            elementToIndex.put(list.get(i), i);
        }
        return true;
    }

    public void forEach(BiConsumer<? super E, Integer> action) {
        for (int i = 0; i < list.size(); ++i) {
            action.accept(list.get(i), i);
        }
    }

    public int size() {
        return list.size();
    }

    public boolean contains(E element) {
        return elementToIndex.containsKey(element);
    }

    public void clear() {
        list.clear();
        elementToIndex.clear();
    }

    @Override
    public String toString() {
        return list.toString();
    }
}