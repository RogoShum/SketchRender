package rogo.sketch.backend.vulkan;

import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.backend.BackendCounterBuffer;
import rogo.sketch.core.resource.descriptor.BufferRole;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.util.KeyId;

final class VulkanCounterBufferResource implements BackendCounterBuffer {
    private final ResolvedBufferResource descriptor;
    private long memoryAddress;
    private boolean disposed;

    VulkanCounterBufferResource(KeyId resourceId, ResolvedBufferResource descriptor) {
        long strideBytes = Math.max(Integer.BYTES, descriptor != null ? descriptor.strideBytes() : Integer.BYTES);
        long counterCount = Math.max(1L, descriptor != null ? descriptor.elementCount() : 1L);
        this.descriptor = new ResolvedBufferResource(
                resourceId != null ? resourceId : KeyId.of("vk_counter_" + System.identityHashCode(this)),
                BufferRole.ATOMIC_COUNTER,
                descriptor != null ? descriptor.updatePolicy() : BufferUpdatePolicy.DYNAMIC,
                counterCount,
                strideBytes,
                Math.max(strideBytes, counterCount * strideBytes));
        this.memoryAddress = MemoryUtil.nmemCalloc(1L, this.descriptor.capacityBytes());
    }

    @Override
    public int handle() {
        return 0;
    }

    @Override
    public long counterCount() {
        return descriptor.elementCount();
    }

    @Override
    public long strideBytes() {
        return descriptor.strideBytes();
    }

    @Override
    public void bind(KeyId resourceType, int binding) {
        // Vulkan atomic counter compatibility is not exposed as a direct bind point.
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (memoryAddress != MemoryUtil.NULL) {
            MemoryUtil.nmemFree(memoryAddress);
            memoryAddress = MemoryUtil.NULL;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}

