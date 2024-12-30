package rogo.sketchrender.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.Collection;
import java.util.Collections;

public class IndexPool<T> {
    private final Object2IntMap<T> objectToIndex = new Object2IntOpenHashMap<>();
    private final Int2ObjectMap<T> indexToObject = new Int2ObjectOpenHashMap<>();

    public IndexPool() {
        objectToIndex.defaultReturnValue(-1);
    }

    /**
     * 添加对象到池中
     * @param object 要添加的对象
     */
    public void add(T object) {
        if (object == null) {
            throw new NullPointerException("Object cannot be null");
        }

        if (objectToIndex.containsKey(object)) {
            return;
        }

        // 直接使用当前对象数量作为新索引
        int newIndex = objectToIndex.size();
        objectToIndex.put(object, newIndex);
        indexToObject.put(newIndex, object);
    }

    /**
     * 移除对象并重新对齐索引
     * @param object 要移除的对象
     * @return 是否成功移除
     */
    public boolean remove(T object) {
        if (object == null) {
            return false;
        }

        Integer oldIndex = objectToIndex.remove(object);
        if (oldIndex == null) {
            return false;
        }

        indexToObject.remove(oldIndex);

        // 找到最大索引
        int maxIndex = objectToIndex.size();

        // 如果删除的不是最后一个元素，需要移动后面的元素
        if (oldIndex < maxIndex) {
            // 移动所有更高索引的对象
            for (int i = oldIndex + 1; i <= maxIndex; i++) {
                T obj = indexToObject.get(i);
                if (obj != null) {
                    // 更新索引
                    indexToObject.remove(i);
                    indexToObject.put(i - 1, obj);
                    objectToIndex.put(obj, i - 1);
                }
            }
        }

        return true;
    }

    /**
     * 获取对象的索引
     * @param object 要查询的对象
     * @return 对象的索引
     * @throws IllegalArgumentException 如果对象不存在于池中
     */
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

    /**
     * 通过索引获取对象
     * @param index 索引
     * @return 对应的对象，如果索引不存在则返回null
     */
    public T getObject(int index) {
        return indexToObject.get(index);
    }

    /**
     * 获取所有对象
     * @return 不可修改的对象集合
     */
    public Collection<T> getAllObjects() {
        return Collections.unmodifiableCollection(objectToIndex.keySet());
    }

    /**
     * 获取所有使用的索引
     * @return 不可修改的索引集合
     */
    public Collection<Integer> getAllIndices() {
        return Collections.unmodifiableCollection(indexToObject.keySet());
    }

    /**
     * 获取当前对象数量
     * @return 对象数量
     */
    public int size() {
        return objectToIndex.size();
    }

    /**
     * 清空池
     */
    public void clear() {
        objectToIndex.clear();
        indexToObject.clear();
    }

    /**
     * 检查是否包含指定对象
     * @param object 要检查的对象
     * @return 是否包含该对象
     */
    public boolean contains(T object) {
        return object != null && objectToIndex.containsKey(object);
    }

    /**
     * 检查是否包含指定索引
     * @param index 要检查的索引
     * @return 是否包含该索引
     */
    public boolean containsIndex(int index) {
        return indexToObject.containsKey(index);
    }
}