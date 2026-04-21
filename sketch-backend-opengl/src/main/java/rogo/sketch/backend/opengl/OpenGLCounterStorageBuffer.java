package rogo.sketch.backend.opengl;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL46;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.data.type.ValueType;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.module.culling.TerrainMeshCounterBuffer;

/**
 * Storage-buffer-backed counter implementation used by phase-B terrain mesh
 * resources. It keeps the buffer backend-safe for shader-storage binding while
 * still allowing indirect draw-count consumers to bind the same handle as a
 * parameter buffer when needed.
 */
final class OpenGLCounterStorageBuffer extends OpenGLStorageBuffer implements TerrainMeshCounterBuffer {
    private final ValueType counterType;
    private long counterCount;

    OpenGLCounterStorageBuffer(ValueType counterType) {
        super(1, Math.max(1L, counterType != null ? counterType.getStride() : Integer.BYTES), GL15.GL_DYNAMIC_DRAW);
        this.counterType = counterType != null ? counterType : ValueType.INT;
        this.counterCount = 1L;
    }

    @Override
    public void resize(int count) {
        int normalizedCount = Math.max(1, count);
        ensureCapacity(normalizedCount, true, true);
        counterCount = normalizedCount;
    }

    @Override
    public void updateCount(int count) {
        long stride = Math.max(1L, strideBytes());
        long address = memoryAddress();
        if (address == 0L) {
            return;
        }
        if (count == 0) {
            MemoryUtil.memSet(address, 0, capacityBytes());
        } else {
            for (long i = 0; i < counterCount; i++) {
                MemoryUtil.memPutInt(address + i * stride, count);
            }
        }
        position(capacityBytes());
        upload();
    }

    @Override
    public void bind() {
        GL43.glBindBuffer(GL46.GL_PARAMETER_BUFFER, getHandle());
    }

    @Override
    public void bind(KeyId resourceType, int binding) {
        KeyId normalizedType = ResourceTypes.normalize(resourceType);
        if (ResourceTypes.STORAGE_BUFFER.equals(normalizedType)) {
            super.bind(resourceType, binding);
            return;
        }
        GL43.glBindBufferBase(GL43.GL_ATOMIC_COUNTER_BUFFER, binding, getHandle());
    }

    @Override
    public int handle() {
        return getHandle();
    }

    @Override
    public long counterCount() {
        return counterCount;
    }

    @Override
    public long strideBytes() {
        return Math.max(1L, counterType.getStride());
    }
}
