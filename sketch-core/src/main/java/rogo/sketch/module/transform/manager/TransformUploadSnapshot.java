package rogo.sketch.module.transform.manager;

import rogo.sketch.core.data.builder.UnsafeBatchBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CPU-side serialized transform snapshot for one compute stream.
 */
public class TransformUploadSnapshot {
    public static final int INPUT_STRIDE = 128;
    public static final int INDEX_STRIDE = 4;

    private UnsafeBatchBuilder inputBuilder;
    private UnsafeBatchBuilder indexBuilder;
    private final List<TransformDispatchRange> dispatchRanges = new ArrayList<>();
    private final Map<Integer, Integer> bindingOffsets = new HashMap<>();
    private int capacity;
    private int activeCount;
    private int maxDepth;

    public TransformUploadSnapshot(int initialCapacity) {
        capacity = Math.max(1, initialCapacity);
        inputBuilder = UnsafeBatchBuilder.createInternal((long) capacity * INPUT_STRIDE);
        indexBuilder = UnsafeBatchBuilder.createInternal((long) capacity * INDEX_STRIDE);
    }

    public void beginBuild(int requiredCount, int maxDepth) {
        ensureCapacity(Math.max(requiredCount, 1));
        inputBuilder.reset();
        indexBuilder.reset();
        dispatchRanges.clear();
        bindingOffsets.clear();
        activeCount = requiredCount;
        this.maxDepth = maxDepth;
    }

    public UnsafeBatchBuilder inputBuilder() {
        return inputBuilder;
    }

    public UnsafeBatchBuilder indexBuilder() {
        return indexBuilder;
    }

    public void addDispatchRange(int offset, int count) {
        dispatchRanges.add(new TransformDispatchRange(offset, count));
    }

    public List<TransformDispatchRange> dispatchRanges() {
        return dispatchRanges;
    }

    public void putBindingOffset(int transformId, int flattenedIndex) {
        bindingOffsets.put(transformId, flattenedIndex);
    }

    public Integer bindingOffset(int transformId) {
        return bindingOffsets.get(transformId);
    }

    public int activeCount() {
        return activeCount;
    }

    public int maxDepth() {
        return maxDepth;
    }

    public long inputSizeBytes() {
        return inputBuilder.getWriteOffset();
    }

    public long indexSizeBytes() {
        return indexBuilder.getWriteOffset();
    }

    public void cleanup() {
        if (inputBuilder != null) {
            inputBuilder.close();
            inputBuilder = null;
        }
        if (indexBuilder != null) {
            indexBuilder.close();
            indexBuilder = null;
        }
        dispatchRanges.clear();
        bindingOffsets.clear();
    }

    private void ensureCapacity(int requiredCount) {
        if (requiredCount <= capacity) {
            return;
        }

        int newCapacity = Math.max(requiredCount, (int) (capacity * 1.5f));
        newCapacity = ((newCapacity + 63) / 64) * 64;
        capacity = newCapacity;

        inputBuilder.close();
        indexBuilder.close();
        inputBuilder = UnsafeBatchBuilder.createInternal((long) capacity * INPUT_STRIDE);
        indexBuilder = UnsafeBatchBuilder.createInternal((long) capacity * INDEX_STRIDE);
    }
}
