package rogo.sketch.module.transform.manager;

import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.data.builder.UnsafeBatchBuilder;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.resource.buffer.ShaderStorageBuffer;
import rogo.sketch.module.transform.TransformData;

import java.util.ArrayList;
import java.util.List;

/**
 * Render-owned GPU input resources and staging buffers for one transform stream.
 */
public class TransformGpuInputSet {
    private ShaderStorageBuffer inputSSBO;
    private UnsafeBatchBuilder inputBuilder;
    private final List<TransformDispatchRange> dispatchRanges = new ArrayList<>();
    private int maxDepth = 0;
    private int capacity;
    private boolean inputDirty = false;

    public TransformGpuInputSet(int initialCapacity) {
        capacity = Math.max(1, initialCapacity);
        inputSSBO = new ShaderStorageBuffer(capacity, TransformUploadSnapshot.INPUT_STRIDE, GL15.GL_DYNAMIC_DRAW);
        inputBuilder = UnsafeBatchBuilder.createInternal((long) capacity * TransformUploadSnapshot.INPUT_STRIDE);
    }

    public void loadSnapshot(TransformUploadSnapshot snapshot) {
        ensureCapacity(Math.max(snapshot.activeCount(), 1));

        inputBuilder.reset();
        dispatchRanges.clear();
        dispatchRanges.addAll(snapshot.dispatchRanges());
        maxDepth = snapshot.maxDepth();

        if (snapshot.inputSizeBytes() > 0) {
            MemoryUtil.memCopy(
                    snapshot.inputBuilder().getBaseAddress(),
                    inputBuilder.getBaseAddress(),
                    snapshot.inputSizeBytes());
            inputBuilder.setWriteOffset(snapshot.inputSizeBytes());
        }

        inputDirty = true;
    }

    public void applyFrameOverrides(List<TransformBinding> frameBindings, TransformUploadSnapshot baseSnapshot) {
        if (baseSnapshot == null || frameBindings.isEmpty()) {
            return;
        }

        boolean patched = false;
        for (TransformBinding binding : frameBindings) {
            Integer offset = baseSnapshot.bindingOffset(binding.transformId());
            if (offset == null) {
                continue;
            }

            int flags = TransformData.computeFlags(
                    binding.frameData(),
                    binding.frameData(),
                    binding.parentTransformId());

            long ptr = inputBuilder.getBaseAddress() + (long) offset * TransformUploadSnapshot.INPUT_STRIDE;
            TransformData.writeToBuffer(
                    binding.frameData(),
                    binding.frameData(),
                    ptr,
                    binding.parentTransformId(),
                    binding.transformId(),
                    flags);
            patched = true;
        }

        if (patched) {
            inputDirty = true;
        }
    }

    public void upload() {
        if (inputDirty) {
            GraphicsDriver.getCurrentAPI().updateBuffer(
                    inputSSBO.getHandle(), 0, inputBuilder.getWriteOffset(), inputBuilder.getBaseAddress());
            inputDirty = false;
        }
    }

    public ShaderStorageBuffer inputSSBO() {
        return inputSSBO;
    }

    public List<TransformDispatchRange> dispatchRanges() {
        return dispatchRanges;
    }

    public int maxDepth() {
        return maxDepth;
    }

    public void cleanup() {
        dispatchRanges.clear();
        if (inputSSBO != null) {
            inputSSBO.dispose();
            inputSSBO = null;
        }
        if (inputBuilder != null) {
            inputBuilder.close();
            inputBuilder = null;
        }
    }

    private void ensureCapacity(int requiredCount) {
        if (requiredCount <= capacity) {
            return;
        }

        int newCapacity = Math.max(requiredCount, (int) (capacity * 1.5f));
        newCapacity = ((newCapacity + 63) / 64) * 64;
        capacity = newCapacity;
        inputSSBO.ensureCapacity(newCapacity, false);

        inputBuilder.close();
        inputBuilder = UnsafeBatchBuilder.createInternal((long) capacity * TransformUploadSnapshot.INPUT_STRIDE);
    }
}
