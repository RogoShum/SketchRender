package rogo.sketch.backend.vulkan;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.backend.BackendInstalledBindableResource;
import rogo.sketch.core.backend.BackendInstalledBuffer;
import rogo.sketch.core.backend.BackendInstalledRenderTarget;
import rogo.sketch.core.backend.BackendInstalledTexture;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.vision.AttachmentBackedRenderTarget;
import rogo.sketch.core.util.KeyId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class VulkanResourceResolver {
    private static final String DIAG_MODULE = "vulkan-resource-resolver";

    private volatile GraphicsResourceManager resourceManager;
    private final Map<KeyId, VulkanTextureResource> textureOverrides = new ConcurrentHashMap<>();
    private final Map<KeyId, VulkanUniformBufferResource> uniformBufferOverrides = new ConcurrentHashMap<>();
    private final Map<KeyId, VulkanDescriptorBufferResource> storageBufferOverrides = new ConcurrentHashMap<>();
    private final Map<KeyId, VulkanCounterBufferResource> counterBufferOverrides = new ConcurrentHashMap<>();
    private final Set<String> warnedIncompatible = ConcurrentHashMap.newKeySet();
    private final Set<String> warnedMissing = ConcurrentHashMap.newKeySet();

    void bindLogicalResourceManager(GraphicsResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    void registerTexture(KeyId resourceId, VulkanTextureResource resource) {
        if (resourceId == null || resource == null) {
            return;
        }
        textureOverrides.put(resourceId, resource);
    }

    void registerUniformBuffer(KeyId resourceId, VulkanUniformBufferResource resource) {
        if (resourceId == null || resource == null) {
            return;
        }
        uniformBufferOverrides.put(resourceId, resource);
    }

    void registerStorageBuffer(KeyId resourceId, VulkanDescriptorBufferResource resource) {
        if (resourceId == null || resource == null) {
            return;
        }
        storageBufferOverrides.put(resourceId, resource);
    }

    void registerCounterBuffer(KeyId resourceId, VulkanCounterBufferResource resource) {
        if (resourceId == null || resource == null) {
            return;
        }
        counterBufferOverrides.put(resourceId, resource);
    }

    VulkanTextureResource resolveTextureResource(KeyId resourceId) {
        if (resourceId == null) {
            return null;
        }
        VulkanTextureResource override = textureOverrides.get(resourceId);
        if (override != null && !override.isDisposed()) {
            return override;
        }
        return resolveFromManager(ResourceTypes.TEXTURE, resourceId, VulkanTextureResource.class);
    }

    VulkanUniformBufferResource resolveUniformBufferResource(KeyId resourceId) {
        if (resourceId == null) {
            return null;
        }
        VulkanUniformBufferResource override = uniformBufferOverrides.get(resourceId);
        if (override != null && !override.isDisposed()) {
            return override;
        }
        return resolveFromManager(ResourceTypes.UNIFORM_BUFFER, resourceId, VulkanUniformBufferResource.class);
    }

    VulkanDescriptorBufferResource resolveStorageBufferResource(KeyId resourceId) {
        if (resourceId == null) {
            return null;
        }
        VulkanDescriptorBufferResource override = storageBufferOverrides.get(resourceId);
        if (override != null && !override.isDisposed()) {
            return override;
        }
        return resolveFromManager(ResourceTypes.STORAGE_BUFFER, resourceId, VulkanDescriptorBufferResource.class);
    }

    VulkanCounterBufferResource resolveCounterBufferResource(KeyId resourceId) {
        if (resourceId == null) {
            return null;
        }
        VulkanCounterBufferResource override = counterBufferOverrides.get(resourceId);
        if (override != null && !override.isDisposed()) {
            return override;
        }
        return resolveFromManager(ResourceTypes.COUNTER_BUFFER, resourceId, VulkanCounterBufferResource.class);
    }

    ResolvedRenderTarget resolveRenderTargetResource(KeyId renderTargetId) {
        if (renderTargetId == null) {
            return null;
        }
        GraphicsResourceManager manager = resourceManager;
        if (manager == null) {
            return null;
        }
        ResourceObject exact = manager.getResourceExact(ResourceTypes.RENDER_TARGET, renderTargetId);
        ResourceObject target = exact != null ? exact : manager.getResource(ResourceTypes.RENDER_TARGET, renderTargetId);
        if (target == null) {
            warnMissing(ResourceTypes.RENDER_TARGET, renderTargetId, exact, null);
            return null;
        }
        if (!(target instanceof AttachmentBackedRenderTarget attachmentBackedRenderTarget)) {
            warnIncompatible(ResourceTypes.RENDER_TARGET, renderTargetId, target, exact, target);
            return null;
        }

        Map<Integer, VulkanTextureResource> colorAttachments = new LinkedHashMap<>();
        int index = 0;
        for (KeyId attachmentId : attachmentBackedRenderTarget.getColorAttachmentIds()) {
            colorAttachments.put(index++, attachmentId != null ? resolveTextureResource(attachmentId) : null);
        }

        KeyId depthAttachmentId = attachmentBackedRenderTarget.getDepthAttachmentId();
        VulkanTextureResource depthAttachment = depthAttachmentId != null ? resolveTextureResource(depthAttachmentId) : null;
        return new ResolvedRenderTarget(renderTargetId, colorAttachments, depthAttachment);
    }

    void destroy() {
        textureOverrides.values().forEach(this::disposeQuietly);
        uniformBufferOverrides.values().forEach(this::disposeQuietly);
        storageBufferOverrides.values().forEach(this::disposeQuietly);
        counterBufferOverrides.values().forEach(this::disposeQuietly);
        textureOverrides.clear();
        uniformBufferOverrides.clear();
        storageBufferOverrides.clear();
        counterBufferOverrides.clear();
        warnedIncompatible.clear();
        warnedMissing.clear();
    }

    private <T extends ResourceObject> T resolveFromManager(KeyId resourceType, KeyId resourceId, Class<T> expectedType) {
        GraphicsResourceManager manager = resourceManager;
        if (manager == null) {
            return null;
        }
        ResourceObject exact = manager.getResourceExact(resourceType, resourceId);
        if (exact == null) {
            ResourceObject inherited = manager.getResource(resourceType, resourceId);
            if (inherited == null) {
                warnMissing(resourceType, resourceId, null, null);
                return null;
            }
            return castOrWarn(resourceType, resourceId, inherited, exact, inherited, expectedType);
        }
        return castOrWarn(resourceType, resourceId, exact, exact, exact, expectedType);
    }

    private <T extends ResourceObject> T castOrWarn(
            KeyId resourceType,
            KeyId resourceId,
            ResourceObject resourceObject,
            ResourceObject exactResource,
            ResourceObject inheritedResource,
            Class<T> expectedType) {
        if (expectedType.isInstance(resourceObject)) {
            return expectedType.cast(resourceObject);
        }
        warnIncompatible(resourceType, resourceId, resourceObject, exactResource, inheritedResource);
        return null;
    }

    private void warnIncompatible(
            KeyId resourceType,
            KeyId resourceId,
            ResourceObject resourceObject,
            ResourceObject exactResource,
            ResourceObject inheritedResource) {
        String key = resourceType + ":" + resourceId + ":" + resourceObject.getClass().getName();
        if (!warnedIncompatible.add(key)) {
            return;
        }
        SketchDiagnostics.get().warn(
                DIAG_MODULE,
                "Resource " + resourceType + ":" + resourceId
                        + " resolved to incompatible object " + resourceObject.getClass().getName()
                        + " for Vulkan backend-native descriptor write"
                        + " [exact=" + describeResource(exactResource)
                        + ", inherited=" + describeResource(inheritedResource) + "]");
    }

    private void warnMissing(KeyId resourceType, KeyId resourceId, ResourceObject exactResource, ResourceObject inheritedResource) {
        String key = resourceType + ":" + resourceId;
        if (!warnedMissing.add(key)) {
            return;
        }
        SketchDiagnostics.get().warn(
                DIAG_MODULE,
                "No Vulkan backend-native resource resolved for " + resourceType + ":" + resourceId
                        + " [exact=" + describeResource(exactResource)
                        + ", inherited=" + describeResource(inheritedResource) + "]");
    }

    private String describeResource(ResourceObject resourceObject) {
        if (resourceObject == null) {
            return "null";
        }
        return resourceObject.getClass().getName() + (resourceObject.isDisposed() ? "(disposed)" : "");
    }

    private void disposeQuietly(ResourceObject resourceObject) {
        if (resourceObject == null || resourceObject.isDisposed()) {
            return;
        }
        try {
            resourceObject.dispose();
        } catch (Exception ignored) {
        }
    }

    record ResolvedRenderTarget(
            KeyId renderTargetId,
            Map<Integer, VulkanTextureResource> colorAttachments,
            VulkanTextureResource depthAttachment) {
    }

    public BackendInstalledBindableResource resolveBindableResource(KeyId resourceType, KeyId resourceId) {
        KeyId normalizedType = ResourceTypes.normalize(resourceType);
        if (ResourceTypes.TEXTURE.equals(normalizedType) || ResourceTypes.IMAGE.equals(normalizedType)) {
            BackendInstalledTexture textureResource = resolveTexture(resourceId);
            return textureResource instanceof BackendInstalledBindableResource bindable ? bindable : null;
        }
        BackendInstalledBuffer buffer = resolveBuffer(normalizedType, resourceId);
        return buffer instanceof BackendInstalledBindableResource bindable ? bindable : null;
    }

    public BackendInstalledTexture resolveTexture(KeyId resourceId) {
        return resolveTextureResource(resourceId);
    }

    public BackendInstalledRenderTarget resolveRenderTarget(KeyId renderTargetId) {
        return null;
    }

    public BackendInstalledBuffer resolveBuffer(KeyId resourceType, KeyId resourceId) {
        KeyId normalizedType = ResourceTypes.normalize(resourceType);
        if (ResourceTypes.UNIFORM_BUFFER.equals(normalizedType)) {
            return resolveUniformBufferResource(resourceId);
        }
        if (ResourceTypes.STORAGE_BUFFER.equals(normalizedType)) {
            return resolveStorageBufferResource(resourceId);
        }
        if (ResourceTypes.ATOMIC_COUNTER.equals(normalizedType) || ResourceTypes.COUNTER_BUFFER.equals(normalizedType)) {
            return resolveCounterBufferResource(resourceId);
        }
        return null;
    }

    ResourceObject resolveLogicalResource(KeyId resourceType, KeyId resourceId) {
        GraphicsResourceManager manager = resourceManager;
        return manager != null && resourceId != null ? manager.getResource(resourceType, resourceId) : null;
    }

    ResourceObject resolveLogicalResourceExact(KeyId resourceType, KeyId resourceId) {
        GraphicsResourceManager manager = resourceManager;
        return manager != null && resourceId != null ? manager.getResourceExact(resourceType, resourceId) : null;
    }
}

