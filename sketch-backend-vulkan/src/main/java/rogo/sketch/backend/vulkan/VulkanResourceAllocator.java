package rogo.sketch.backend.vulkan;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.backend.BackendCounterBuffer;
import rogo.sketch.core.backend.BackendIndirectBuffer;
import rogo.sketch.core.backend.BackendInstalledBindableResource;
import rogo.sketch.core.backend.BackendInstalledBuffer;
import rogo.sketch.core.backend.BackendInstalledRenderTarget;
import rogo.sketch.core.backend.BackendInstalledTexture;
import rogo.sketch.core.backend.BackendReadbackBuffer;
import rogo.sketch.core.backend.BackendStorageBuffer;
import rogo.sketch.core.backend.BackendUniformBuffer;
import rogo.sketch.core.backend.GpuHandle;
import rogo.sketch.core.backend.LogicalResourceRegistryBinder;
import rogo.sketch.core.backend.ResourceAllocator;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.packet.ResourceSetKey;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.resource.descriptor.ResolvedRenderTargetSpec;
import rogo.sketch.core.resource.vision.RenderTarget;
import rogo.sketch.core.resource.vision.StandardRenderTarget;
import rogo.sketch.core.resource.vision.StandardTexture;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.util.KeyId;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;

final class VulkanResourceAllocator implements ResourceAllocator, LogicalResourceRegistryBinder {
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
    public Texture installTexture(
            KeyId resourceId,
            ResolvedImageResource descriptor,
            @Nullable String imagePath,
            @Nullable ByteBuffer imageData) {
        StandardTexture texture = new StandardTexture(GpuHandle.NONE, resourceId, descriptor, imagePath);
        texture.updateCurrentSize(descriptor.width(), descriptor.height());
        return texture;
    }

    @Override
    public RenderTarget installRenderTarget(KeyId resourceId, ResolvedRenderTargetSpec descriptor) {
        return new StandardRenderTarget(GpuHandle.NONE, resourceId, descriptor, null);
    }

    @Override
    public BackendUniformBuffer installUniformBuffer(
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
    public BackendStorageBuffer installStorageBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData) {
        return runtime.createStorageBufferResource(resourceId, descriptor, initialData);
    }

    @Override
    public BackendCounterBuffer installCounterBuffer(
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
    public BackendIndirectBuffer installIndirectBuffer(
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
    public BackendReadbackBuffer installReadbackBuffer(
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
    public void installImmediateResourceBindings(List<RenderPacket> packets) {
        if (packets == null || packets.isEmpty()) {
            return;
        }
        LinkedHashMap<ResourceSetKey, FrameExecutionPlan.ResourceUploadPlan> uploadPlans = new LinkedHashMap<>();
        for (RenderPacket packet : packets) {
            if (packet == null) {
                continue;
            }
            ResourceBindingPlan bindingPlan = packet.bindingPlan();
            if (bindingPlan == null || bindingPlan.isEmpty()) {
                continue;
            }
            ResourceSetKey resourceSetKey = packet.resourceSetKey();
            if (resourceSetKey == null || resourceSetKey.isEmpty()) {
                resourceSetKey = ResourceSetKey.from(bindingPlan, packet.uniformGroups().resourceUniforms());
            }
            ExecutionKey stateKey = packet.stateKey();
            FrameExecutionPlan.ResourceUploadPlan uploadPlan = new FrameExecutionPlan.ResourceUploadPlan(
                    packet.stageId(),
                    resourceSetKey,
                    bindingPlan,
                    packet.uniformGroups(),
                    stateKey != null ? stateKey.shaderId() : KeyId.of("sketch:unbound_shader"),
                    bindingPlan.layoutKey());
            uploadPlans.put(resourceSetKey, uploadPlan);
        }
        if (!uploadPlans.isEmpty()) {
            descriptorArena.install(List.copyOf(uploadPlans.values()));
        }
    }

    @Override
    public BackendInstalledBindableResource resolveBindableResource(KeyId resourceType, KeyId resourceId) {
        return resourceResolver.resolveBindableResource(resourceType, resourceId);
    }

    @Override
    public BackendInstalledTexture resolveTexture(KeyId resourceId) {
        return resourceResolver.resolveTexture(resourceId);
    }

    @Override
    public BackendInstalledRenderTarget resolveRenderTarget(KeyId renderTargetId) {
        return resourceResolver.resolveRenderTarget(renderTargetId);
    }

    @Override
    public BackendInstalledBuffer resolveBuffer(KeyId resourceType, KeyId resourceId) {
        return resourceResolver.resolveBuffer(resourceType, resourceId);
    }

    @Override
    public ResourceObject resolveLogicalResource(KeyId resourceType, KeyId resourceId) {
        return resourceResolver.resolveLogicalResource(resourceType, resourceId);
    }

    @Override
    public ResourceObject resolveLogicalResourceExact(KeyId resourceType, KeyId resourceId) {
        return resourceResolver.resolveLogicalResourceExact(resourceType, resourceId);
    }

    @Override
    public void bindLogicalResourceRegistry(GraphicsResourceManager resourceManager) {
        resourceResolver.bindLogicalResourceManager(resourceManager);
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
