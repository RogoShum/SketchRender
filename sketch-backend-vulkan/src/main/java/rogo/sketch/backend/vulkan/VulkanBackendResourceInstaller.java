package rogo.sketch.backend.vulkan;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.backend.BackendCounterBuffer;
import rogo.sketch.core.backend.BackendIndirectBuffer;
import rogo.sketch.core.backend.BackendResourceInstaller;
import rogo.sketch.core.backend.BackendStorageBuffer;
import rogo.sketch.core.backend.BackendUniformBuffer;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.resource.descriptor.ResolvedRenderTargetSpec;
import rogo.sketch.core.resource.vision.RenderTarget;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.util.KeyId;

import java.nio.ByteBuffer;

final class VulkanBackendResourceInstaller implements BackendResourceInstaller {
    private final VulkanResourceAllocator allocator;

    VulkanBackendResourceInstaller(VulkanResourceAllocator allocator) {
        this.allocator = allocator;
    }

    @Override
    public Texture createTexture(
            KeyId resourceId,
            ResolvedImageResource descriptor,
            @Nullable String imagePath,
            @Nullable ByteBuffer imageData) {
        return allocator.createTexture(resourceId, descriptor, imagePath, imageData);
    }

    @Override
    public RenderTarget createRenderTarget(KeyId resourceId, ResolvedRenderTargetSpec descriptor) {
        return allocator.createRenderTarget(resourceId, descriptor);
    }

    @Override
    public BackendUniformBuffer createUniformBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData) {
        return allocator.createUniformBuffer(resourceId, descriptor, initialData);
    }

    @Override
    public BackendStorageBuffer createStorageBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData) {
        return allocator.createStorageBuffer(resourceId, descriptor, initialData);
    }

    @Override
    public BackendCounterBuffer createCounterBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData) {
        return allocator.createCounterBuffer(resourceId, descriptor, initialData);
    }

    @Override
    public BackendIndirectBuffer createIndirectBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            long commandCapacity) {
        return allocator.createIndirectBuffer(resourceId, descriptor, commandCapacity);
    }
}
