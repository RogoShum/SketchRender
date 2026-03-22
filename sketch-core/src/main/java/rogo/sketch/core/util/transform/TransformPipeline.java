package rogo.sketch.core.util.transform;

import org.lwjgl.opengl.GL15;
import rogo.sketch.core.data.builder.UnsafeBatchBuilder;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.resource.buffer.ShaderStorageBuffer;
import rogo.sketch.core.transform.TransformData;

import java.util.ArrayList;
import java.util.List;

/**
 * CPU/GPU upload pipeline for a subset of transform bindings.
 * <p>
 * Tick-side and frame-side transform collection only mutate CPU-side builders.
 * The frame graph performs the final SSBO upload after all authored data is ready.
 * At interpolation-build time it serializes transform data using:
 * <ul>
 *   <li>the current tick buffer as the previous interpolation sample</li>
 *   <li>the pending write buffer as the current interpolation sample</li>
 * </ul>
 * Frame-authored bindings contribute a frame-local sample that is written as both
 * interpolation endpoints.
 */
public class TransformPipeline {
    private static final int INPUT_STRIDE = 128;
    // 4 bytes index stride (int)
    private static final int INDEX_STRIDE = 4;

    // Hierarchy management: List of Layers, each Layer is a List of bindings
    private final List<List<TransformBinding>> depthLayers = new ArrayList<>();
    private int maxDepth = 0;
    private int activeCount = 0;

    // Buffers & Builders
    private ShaderStorageBuffer inputSSBO;
    private ShaderStorageBuffer indexSSBO;

    // Builders using Off-Heap Memory (Unsafe)
    private UnsafeBatchBuilder inputBuilder;
    private UnsafeBatchBuilder indexBuilder;

    // State flags
    private boolean structureDirty = true;
    private boolean dataReady = false;
    private boolean indexReady = false;

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

    public void add(TransformBinding binding) {
        int depth = binding.depth();
        while (depthLayers.size() <= depth) {
            depthLayers.add(new ArrayList<>());
        }
        depthLayers.get(depth).add(binding);
        maxDepth = Math.max(maxDepth, depth);
        activeCount++;
        structureDirty = true; // Indexes must be rebuilt
        indexReady = false;
        ensureCapacity();
    }

    public void remove(TransformBinding binding) {
        int depth = binding.depth();
        if (depth < depthLayers.size()) {
            List<TransformBinding> layer = depthLayers.get(depth);
            // Use swap-remove for O(1), order change is fine because we rebuild indices
            int idx = layer.indexOf(binding);
            if (idx != -1) {
                int lastIdx = layer.size() - 1;
                if (idx != lastIdx) {
                    layer.set(idx, layer.get(lastIdx));
                }
                layer.remove(lastIdx);
                activeCount--;
                structureDirty = true;
                indexReady = false;
            }
        }
    }

    public void onDepthChanged(TransformBinding binding, int oldDepth) {
        if (oldDepth < depthLayers.size()) {
            depthLayers.get(oldDepth).remove(binding);
        }
        int depth = binding.depth();
        while (depthLayers.size() <= depth) {
            depthLayers.add(new ArrayList<>());
        }
        depthLayers.get(depth).add(binding);
        maxDepth = Math.max(maxDepth, depth);
        structureDirty = true;
        indexReady = false;
    }

    private void ensureCapacity() {
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
            indexReady = false;
        }
    }

    // ================== Tick Upload Preparation ==================

    /**
     * Build transform upload data for interpolation.
     * Uses the current tick buffer as previous and the pending write buffer as current.
     */
    public void prepareInterpolationData(boolean frameSync) {
        if (activeCount == 0) return;

        inputBuilder.reset();

        long currentPtr = inputBuilder.getBaseAddress();

        if (frameSync) {
            for (int d = 0; d <= maxDepth; d++) {
                if (d >= depthLayers.size()) break;
                List<TransformBinding> layer = depthLayers.get(d);

                for (int i = 0; i < layer.size(); i++) {
                    TransformBinding binding = layer.get(i);


                    if (binding.updateDomain() == TransformUpdateDomain.SYNC_FRAME) {
                        TransformData.writeToBuffer(
                                binding.frameData(),
                                binding.frameData(),
                                currentPtr,
                                binding.parentTransformId());
                    }

                    currentPtr += INPUT_STRIDE;
                }
            }
        } else {
            for (int d = 0; d <= maxDepth; d++) {
                if (d >= depthLayers.size()) break;
                List<TransformBinding> layer = depthLayers.get(d);

                for (int i = 0; i < layer.size(); i++) {
                    TransformBinding binding = layer.get(i);
                    TransformData.writeToBuffer(
                            binding.currentTickData(),
                            binding.pendingTickData(),
                            currentPtr,
                            binding.parentTransformId());

                    currentPtr += INPUT_STRIDE;
                }
            }
        }

        inputBuilder.setWriteOffset(currentPtr - inputBuilder.getBaseAddress());
        markDataDirty();
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

            List<TransformBinding> layer = depthLayers.get(d);
            int count = layer.size();

            if (count > 0) {
                // Record the range for this layer
                dispatchRanges.add(new LayerDispatchRange(currentOffset, count));

                // Write Real IDs to builder
                for (int i = 0; i < count; i++) {
                    indexBuilder.put(layer.get(i).transformId());
                }
                currentOffset += count;
            } else {
                dispatchRanges.add(new LayerDispatchRange(0, 0));
            }
        }
    }

    public void prepareStructureBuffers() {
        ensureCapacity();
        if (!structureDirty) {
            return;
        }
        rebuildIndices();
        structureDirty = false;
        indexReady = true;
    }

    // ================== GPU Upload (Main Thread) ==================

    public void upload() {
        if (activeCount == 0) return;

        if (indexReady) {
            GraphicsDriver.getCurrentAPI().updateBuffer(indexSSBO.getHandle(), 0, indexBuilder.getWriteOffset(), indexBuilder.getBaseAddress());
            indexReady = false;
        }

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

    public void markDataDirty() {
        dataReady = true;
    }

    public boolean isStructureDirty() {
        return structureDirty;
    }

    public boolean isDataReady() {
        return dataReady;
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