package rogo.sketch.backend.vulkan;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.backend.BackendCounterBuffer;
import rogo.sketch.core.backend.BackendIndirectBuffer;
import rogo.sketch.core.backend.BackendReadbackBuffer;
import rogo.sketch.core.backend.BackendStorageBuffer;
import rogo.sketch.core.backend.BackendUniformBuffer;
import rogo.sketch.core.backend.ResourceAllocator;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.resource.descriptor.ResolvedRenderTargetSpec;
import rogo.sketch.core.resource.vision.RenderTarget;
import rogo.sketch.core.resource.vision.StandardRenderTarget;
import rogo.sketch.core.resource.vision.StandardTexture;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.util.KeyId;

import java.nio.ByteBuffer;

final class VulkanResourceAllocator implements ResourceAllocator {
    private final VulkanBackendRuntime runtime;
    private final VulkanResourceResolver resourceResolver;
    private final VulkanDescriptorArena descriptorArena;
    private final VulkanGeometryArena geometryArena;

    VulkanResourceAllocator(VulkanBackendRuntime runtime, VulkanResourceResolver resourceResolver) {
        this.runtime = runtime;
        this.resourceResolver = resourceResolver;
        this.descriptorArena = new VulkanDescriptorArena(runtime.device(), resourceResolver);
        this.geometryArena = new VulkanGeometryArena(runtime.physicalDevice(), runtime.device());
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
        VulkanCounterBufferResource resource = new VulkanCounterBufferResource(
                resourceId,
                runtime.physicalDevice(),
                runtime.device(),
                descriptor);
        runtime.registerCounterBufferResource(resourceId, resource);
        return resource;
    }

    @Override
    public BackendIndirectBuffer createIndirectBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            long commandCapacity) {
        return new VulkanIndirectBufferResource(
                resourceId,
                runtime.physicalDevice(),
                runtime.device(),
                commandCapacity > 0L ? commandCapacity : Math.max(1L, descriptor.elementCount()));
    }

    @Override
    public BackendReadbackBuffer createReadbackBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            int initialElementCapacity) {
        long strideBytes = descriptor != null ? Math.max(1L, descriptor.strideBytes()) : Integer.BYTES;
        return new VulkanReadbackBufferResource(
                resourceId,
                runtime.physicalDevice(),
                runtime.device(),
                Math.max(1, initialElementCapacity),
                strideBytes);
    }

    @Override
    public void installExecutionPlan(FrameExecutionPlan plan, long frameEpoch, int framesInFlight) {
        FrameExecutionPlan nextExecutionPlan = plan != null ? plan : FrameExecutionPlan.empty();
        geometryArena.install(nextExecutionPlan.geometryUploadPlans(), frameEpoch, framesInFlight);
        descriptorArena.install(nextExecutionPlan.resourceUploadPlans());
    }

    @Override
    public void shutdown() {
        geometryArena.destroy();
        descriptorArena.destroy();
    }

    VulkanDescriptorArena descriptorArena() {
        return descriptorArena;
    }

    VulkanGeometryArena geometryArena() {
        return geometryArena;
    }

    VulkanResourceResolver resourceResolver() {
        return resourceResolver;
    }
}
