package rogo.sketch.core.util.transform;

import org.lwjgl.opengl.GL15;
import rogo.sketch.core.data.builder.UnsafeBatchBuilder;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.resource.buffer.ShaderStorageBuffer;
import rogo.sketch.core.transform.Transform;

import java.util.ArrayList;
import java.util.List;

/**
 * A standalone pipeline that manages a subset of transforms (Sync or Async).
 * Handles:
 * - Depth-sorted storage
 * - Input Data generation (CPU -> Builder)
 * - Index Data generation (Structure -> Builder)
 * - GPU Upload
 */
public class TransformPipeline {
    // 128 bytes input stride
    private static final int INPUT_STRIDE = Transform.SSBO_STRIDE;
    // 4 bytes index stride (int)
    private static final int INDEX_STRIDE = 4;

    // Hierarchy management: List of Layers, each Layer is a List of Transforms
    private final List<List<Transform>> depthLayers = new ArrayList<>();
    private int maxDepth = 0;
    private int activeCount = 0;

    // Buffers & Builders
    private ShaderStorageBuffer inputSSBO;
    private ShaderStorageBuffer indexSSBO;

    // Builders using Off-Heap Memory (Unsafe)
    private UnsafeBatchBuilder inputBuilder;
    private UnsafeBatchBuilder indexBuilder;

    // State flags
    private boolean structureDirty = true; // True if objects added/removed/depth-changed
    private boolean dataReady = false;     // True if logic processed and ready for upload

    // Dispatch ranges for Compute Shader
    private final List<LayerDispatchRange> dispatchRanges = new ArrayList<>();

    public TransformPipeline(int initialCapacity) {
        initializeResources(initialCapacity);
    }

    private void initializeResources(int capacity) {
        long inputSize = (long) capacity * INPUT_STRIDE;
        long indexSize = (long) capacity * INDEX_STRIDE;

        inputSSBO = new ShaderStorageBuffer(capacity, INPUT_STRIDE, GL15.GL_DYNAMIC_DRAW);
        indexSSBO = new ShaderStorageBuffer(capacity, INDEX_STRIDE, GL15.GL_DYNAMIC_DRAW);

        inputBuilder = UnsafeBatchBuilder.createInternal(inputSize);
        indexBuilder = UnsafeBatchBuilder.createInternal(indexSize);
    }

    // ================== Management (Main Thread) ==================

    public void add(Transform t) {
        int depth = t.getDepth();
        while (depthLayers.size() <= depth) {
            depthLayers.add(new ArrayList<>());
        }
        depthLayers.get(depth).add(t);
        maxDepth = Math.max(maxDepth, depth);
        activeCount++;
        structureDirty = true; // Indexes must be rebuilt
        checkCapacity();
    }

    public void remove(Transform t) {
        int depth = t.getDepth();
        if (depth < depthLayers.size()) {
            List<Transform> layer = depthLayers.get(depth);
            // Use swap-remove for O(1), order change is fine because we rebuild indices
            int idx = layer.indexOf(t);
            if (idx != -1) {
                int lastIdx = layer.size() - 1;
                if (idx != lastIdx) {
                    layer.set(idx, layer.get(lastIdx));
                }
                layer.remove(lastIdx);
                activeCount--;
                structureDirty = true;
            }
        }
    }

    public void onDepthChanged(Transform t, int oldDepth) {
        // Remove from old layer (manual logic to avoid full remove() overhead if needed)
        if (oldDepth < depthLayers.size()) {
            depthLayers.get(oldDepth).remove(t); // Optimization: Swap-remove here too
        }
        // Add to new
        add(t);
        // Note: activeCount doesn't change, but structure is dirty
    }

    private void checkCapacity() {
        long currentCap = inputSSBO.getDataCount();
        if (activeCount > currentCap) {
            int newCap = Math.max(activeCount, (int) (currentCap * 1.5));
            // Align to 64
            newCap = ((newCap + 63) / 64) * 64;

            // Resize SSBOs
            inputSSBO.ensureCapacity(newCap, false);
            indexSSBO.ensureCapacity(newCap, false);

            // Recreate Builders
            inputBuilder.close();
            indexBuilder.close();
            inputBuilder = UnsafeBatchBuilder.createInternal((long) newCap * INPUT_STRIDE);
            indexBuilder = UnsafeBatchBuilder.createInternal((long) newCap * INDEX_STRIDE);

            structureDirty = true; // Force index rebuild
        }
    }

    // ================== Logic Processing (Thread Agnostic) ==================

    /**
     * Processing Logic.
     * Can be called from Main Thread (Sync) or Worker Thread (Async).
     * Writes Transform data to the Input Builder.
     */
    public void processLogic() {
        if (activeCount == 0) return;

        inputBuilder.reset();

        // 1. 获取起始内存地址
        long currentPtr = inputBuilder.getBaseAddress();

        // IMPORTANT: Must iterate in exactly the same order as rebuildIndices()
        // We iterate depth 0 -> maxDepth
        for (int d = 0; d <= maxDepth; d++) {
            if (d >= depthLayers.size()) break;
            List<Transform> layer = depthLayers.get(d);

            for (int i = 0; i < layer.size(); i++) {
                Transform t = layer.get(i);
                int parentId = (t.getParent() != null) ? t.getParent().getRegisteredId() : -1;

                // 2. 写入数据到当前指针位置 (使用 ptr)
                t.swapData();
                t.writeToBuffer(currentPtr, parentId);

                // 3. 关键修正：指针后移一个 Stride (128字节)，指向下一个对象的内存槽位
                currentPtr += INPUT_STRIDE;
            }
        }

        inputBuilder.setWriteOffset(currentPtr - inputBuilder.getBaseAddress());
        dataReady = true;
    }

    // ================== Structure Update (Main Thread) ==================

    /**
     * Rebuilds the Index Builder.
     * Must be called when structure is dirty, BEFORE uploading.
     */
    private void rebuildIndices() {
        indexBuilder.reset();
        dispatchRanges.clear();

        int currentOffset = 0;

        for (int d = 0; d <= maxDepth; d++) {
            if (d >= depthLayers.size()) {
                dispatchRanges.add(new LayerDispatchRange(0, 0));
                continue;
            }

            List<Transform> layer = depthLayers.get(d);
            int count = layer.size();

            if (count > 0) {
                // Record the range for this layer
                dispatchRanges.add(new LayerDispatchRange(currentOffset, count));

                // Write Real IDs to builder
                for (int i = 0; i < count; i++) {
                    indexBuilder.put(layer.get(i).getRegisteredId());
                }
                currentOffset += count;
            } else {
                dispatchRanges.add(new LayerDispatchRange(0, 0));
            }
        }
    }

    // ================== GPU Upload (Main Thread) ==================

    public void upload() {
        if (activeCount == 0) return;

        // 1. Handle Structure Changes (Index Buffer)
        if (structureDirty) {
            rebuildIndices();

            GraphicsDriver.getCurrentAPI().updateBuffer(indexSSBO.getHandle(), 0, indexBuilder.getWriteOffset(), indexBuilder.getBaseAddress());
            structureDirty = false;
        }

        // 2. Handle Data Update (Input Buffer)
        if (dataReady) {
            GraphicsDriver.getCurrentAPI().updateBuffer(inputSSBO.getHandle(), 0, inputBuilder.getWriteOffset(), inputBuilder.getBaseAddress());
            dataReady = false;
        }
    }

    public ShaderStorageBuffer indexSSBO() {
        return indexSSBO;
    }

    public ShaderStorageBuffer inputSSBO() {
        return inputSSBO;
    }

    public List<LayerDispatchRange> getDispatchRanges() {
        return dispatchRanges;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void cleanup() {
        depthLayers.clear();
        dispatchRanges.clear();
        if (inputSSBO != null) inputSSBO.dispose();
        if (indexSSBO != null) indexSSBO.dispose();
        if (inputBuilder != null) inputBuilder.close();
        if (indexBuilder != null) indexBuilder.close();
    }

    public record LayerDispatchRange(int offset, int count) {
    }
}