package rogo.sketch.module.transform.manager;

import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.backend.BackendBufferFactory;
import rogo.sketch.core.backend.BackendStorageBuffer;
import rogo.sketch.core.data.builder.NativeWriteBuffer;
import rogo.sketch.core.resource.descriptor.BufferRole;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.module.transform.TransformData;

import java.util.ArrayList;
import java.util.List;

/**
 * Render-owned GPU input resources and staging buffers for one transform stream.
 */
public class TransformGpuInputSet {
    private final KeyId bufferId;
    private BackendStorageBuffer inputSSBO;
    private NativeWriteBuffer inputBuilder;
    private final List<TransformDispatchRange> dispatchRanges = new ArrayList<>();
    private int maxDepth = 0;
    private int capacity;
    private boolean inputDirty = false;

    public TransformGpuInputSet(int initialCapacity) {
        capacity = Math.max(1, initialCapacity);
        bufferId = KeyId.of("sketch_render:transform_input_runtime_" + Integer.toUnsignedString(System.identityHashCode(this)));
        inputSSBO = BackendBufferFactory.createStorageBuffer(bufferId, descriptorFor(capacity), null);
        inputBuilder = NativeWriteBuffer.createInternal((long) capacity * TransformUploadSnapshot.INPUT_STRIDE);
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
            inputSSBO.upload(inputBuilder.getBaseAddress(), inputBuilder.getWriteOffset());
            inputDirty = false;
        }
    }

    public BackendStorageBuffer inputSSBO() {
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
        inputBuilder = NativeWriteBuffer.createInternal((long) capacity * TransformUploadSnapshot.INPUT_STRIDE);
    }

    private ResolvedBufferResource descriptorFor(int elementCount) {
        long safeCount = Math.max(1L, elementCount);
        long stride = TransformUploadSnapshot.INPUT_STRIDE;
        return new ResolvedBufferResource(
                bufferId,
                BufferRole.STORAGE,
                BufferUpdatePolicy.DYNAMIC,
                safeCount,
                stride,
                safeCount * stride);
    }
}

