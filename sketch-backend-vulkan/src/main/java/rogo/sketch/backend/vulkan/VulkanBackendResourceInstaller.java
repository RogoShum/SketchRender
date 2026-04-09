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
import rogo.sketch.core.resource.vision.StandardRenderTarget;
import rogo.sketch.core.resource.vision.StandardTexture;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.util.KeyId;

import java.nio.ByteBuffer;

final class VulkanBackendResourceInstaller implements BackendResourceInstaller {
    private final VulkanBackendRuntime runtime;

    VulkanBackendResourceInstaller(VulkanBackendRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public Texture createTexture(
            KeyId resourceId,
            ResolvedImageResource descriptor,
            @Nullable String imagePath,
            @Nullable ByteBuffer imageData) {
        StandardTexture texture = new StandardTexture(0, resourceId, descriptor, imagePath);
        texture.updateCurrentSize(descriptor.width(), descriptor.height());
        return texture;
    }

    @Override
    public RenderTarget createRenderTarget(KeyId resourceId, ResolvedRenderTargetSpec descriptor) {
        return new StandardRenderTarget(0, resourceId, descriptor, null);
    }

    @Override
    public BackendUniformBuffer createUniformBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData) {
        VulkanUniformBufferResource resource = new VulkanUniformBufferResource(
                resourceId,
                runtime.physicalDevice(),
                runtime.device(),
                Math.max(16L, descriptor.capacityBytes()));
        if (initialData != null) {
            resource.update(initialData);
        }
        return resource;
    }

    @Override
    public BackendStorageBuffer createStorageBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData) {
        return runtime.createStorageBufferResource(resourceId, descriptor, initialData);
    }

    @Override
    public BackendCounterBuffer createCounterBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData) {
        VulkanCounterBufferResource resource = new VulkanCounterBufferResource(resourceId, descriptor);
        runtime.registerCounterBufferResource(resourceId, resource);
        return resource;
    }

    @Override
    public BackendIndirectBuffer createIndirectBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            long commandCapacity) {
        return new VulkanIndirectBufferResource(commandCapacity > 0L ? commandCapacity : Math.max(1L, descriptor.elementCount()));
    }
}

