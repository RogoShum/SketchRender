package rogo.sketch.core.shader.uniform;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Arrays;
import java.util.Map;

final class UniformCaptureBuffer {
    private static final int INITIAL_CAPACITY = 8;
    private static final ThreadLocal<UniformCaptureBuffer> SCRATCH =
            ThreadLocal.withInitial(UniformCaptureBuffer::new);

    private final Object2IntOpenHashMap<String> indexByName = new Object2IntOpenHashMap<>();
    private String[] names = new String[INITIAL_CAPACITY];
    private Object[] values = new Object[INITIAL_CAPACITY];
    private int size;

    private UniformCaptureBuffer() {
        indexByName.defaultReturnValue(-1);
    }

    static UniformCaptureBuffer acquire() {
        UniformCaptureBuffer buffer = SCRATCH.get();
        buffer.reset();
        return buffer;
    }

    void put(String uniformName, Object value) {
        if (uniformName == null || value == null) {
            return;
        }
        int existing = indexByName.getInt(uniformName);
        if (existing >= 0) {
            values[existing] = value;
            return;
        }
        ensureCapacity(size + 1);
        indexByName.put(uniformName, size);
        names[size] = uniformName;
        values[size] = value;
        size++;
    }

    UniformValueSnapshot snapshot() {
        if (size == 0) {
            return UniformValueSnapshot.empty();
        }
        String[] packedNames = Arrays.copyOf(names, size);
        Object[] packedValues = Arrays.copyOf(values, size);
        sortByName(packedNames, packedValues);
        return UniformValueSnapshot.fromSorted(packedNames, packedValues);
    }

    Map<String, Object> toMap() {
        if (size == 0) {
            return Map.of();
        }
        Object2ObjectOpenHashMap<String, Object> directValues = new Object2ObjectOpenHashMap<>(size);
        for (int i = 0; i < size; i++) {
            directValues.put(names[i], values[i]);
        }
        directValues.trim();
        return directValues;
    }

    private void reset() {
        if (size > 0) {
            Arrays.fill(names, 0, size, null);
            Arrays.fill(values, 0, size, null);
        }
        indexByName.clear();
        size = 0;
    }

    private void ensureCapacity(int requiredSize) {
        if (requiredSize <= names.length) {
            return;
        }
        int nextCapacity = Math.max(requiredSize, names.length * 2);
        names = Arrays.copyOf(names, nextCapacity);
        values = Arrays.copyOf(values, nextCapacity);
    }

    private static void sortByName(String[] names, Object[] values) {
        for (int i = 1; i < names.length; i++) {
            String name = names[i];
            Object value = values[i];
            int cursor = i - 1;
            while (cursor >= 0 && names[cursor].compareTo(name) > 0) {
                names[cursor + 1] = names[cursor];
                values[cursor + 1] = values[cursor];
                cursor--;
            }
            names[cursor + 1] = name;
            values[cursor + 1] = value;
        }
    }
}
