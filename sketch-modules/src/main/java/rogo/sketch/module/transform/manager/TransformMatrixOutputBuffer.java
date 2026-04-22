package rogo.sketch.module.transform.manager;

import rogo.sketch.core.backend.BackendBufferFactory;
import rogo.sketch.core.backend.BackendStorageBuffer;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.resource.descriptor.BufferRole;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.util.KeyId;

/**
 * GPU output SSBO for computed transform matrices.
 */
public class TransformMatrixOutputBuffer {
    private static final int OUTPUT_STRIDE = 64;

    private final KeyId bufferId = KeyId.of("sketch_render:transform_output_runtime");
    private final BackendStorageBuffer outputSSBO;
    private int currentCapacity = 64;

    public TransformMatrixOutputBuffer() {
        outputSSBO = BackendBufferFactory.createStorageBuffer(bufferId, descriptorFor(currentCapacity), null);
    }

    public void ensureCapacityForMaxId(int maxAllocatedId) {
        if (maxAllocatedId <= currentCapacity) {
            return;
        }

        int newCapacity = Math.max(maxAllocatedId, (int) (currentCapacity * 1.5f));
        newCapacity = ((newCapacity + 63) / 64) * 64;
        outputSSBO.ensureCapacity(newCapacity, false);
        currentCapacity = newCapacity;
    }

    public ResourceObject resource() {
        return outputSSBO;
    }

    public void cleanup() {
        outputSSBO.dispose();
    }

    private ResolvedBufferResource descriptorFor(int elementCount) {
        long safeCount = Math.max(1L, elementCount);
        return new ResolvedBufferResource(
                bufferId,
                BufferRole.STORAGE,
                BufferUpdatePolicy.DYNAMIC,
                safeCount,
                OUTPUT_STRIDE,
                safeCount * OUTPUT_STRIDE);
    }
}

