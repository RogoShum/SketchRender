package rogo.sketch.module.transform.manager;

import org.lwjgl.opengl.GL15;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.resource.buffer.ShaderStorageBuffer;

/**
 * GPU output SSBO for computed transform matrices.
 */
public class TransformMatrixOutputBuffer {
    private static final int OUTPUT_STRIDE = 64;

    private final ShaderStorageBuffer outputSSBO;
    private int currentCapacity = 64;

    public TransformMatrixOutputBuffer() {
        outputSSBO = new ShaderStorageBuffer(currentCapacity, OUTPUT_STRIDE, GL15.GL_DYNAMIC_DRAW);
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
}
