package rogo.sketch.backend.vulkan;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.backend.BackendInstalledBindableResource;
import rogo.sketch.core.backend.BackendInstalledBuffer;
import rogo.sketch.core.backend.BackendInstalledRenderTarget;
import rogo.sketch.core.backend.BackendInstalledTexture;
import rogo.sketch.core.backend.BackendResourceResolver;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.vision.AttachmentBackedRenderTarget;
import rogo.sketch.core.util.KeyId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class VulkanResourceResolver implements BackendResourceResolver {
    private static final String DIAG_MODULE = "vulkan-resource-resolver";

    private final GraphicsResourceManager resourceManager = GraphicsResourceManager.getInstance();
    private final Map<KeyId, VulkanTextureResource> textureOverrides = new ConcurrentHashMap<>();
    private final Map<KeyId, VulkanUniformBufferResource> uniformBufferOverrides = new ConcurrentHashMap<>();
    private final Map<KeyId, VulkanStorageBufferResource> storageBufferOverrides = new ConcurrentHashMap<>();
    private final Map<KeyId, VulkanCounterBufferResource> counterBufferOverrides = new ConcurrentHashMap<>();
    private final Set<String> warnedIncompatible = ConcurrentHashMap.newKeySet();
    private final Set<String> warnedMissing = ConcurrentHashMap.newKeySet();

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

    void registerStorageBuffer(KeyId resourceId, VulkanStorageBufferResource resource) {
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

    VulkanStorageBufferResource resolveStorageBufferResource(KeyId resourceId) {
        if (resourceId == null) {
            return null;
        }
        VulkanStorageBufferResource override = storageBufferOverrides.get(resourceId);
        if (override != null && !override.isDisposed()) {
            return override;
        }
        return resolveFromManager(ResourceTypes.STORAGE_BUFFER, resourceId, VulkanStorageBufferResource.class);
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
        ResourceObject exact = resourceManager.getResourceExact(ResourceTypes.RENDER_TARGET, renderTargetId);
        ResourceObject target = exact != null ? exact : resourceManager.getResource(ResourceTypes.RENDER_TARGET, renderTargetId);
        if (target == null) {
            warnMissing(ResourceTypes.RENDER_TARGET, renderTargetId);
            return null;
        }
        if (!(target instanceof AttachmentBackedRenderTarget attachmentBackedRenderTarget)) {
            warnIncompatible(ResourceTypes.RENDER_TARGET, renderTargetId, target);
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
        ResourceObject exact = resourceManager.getResourceExact(resourceType, resourceId);
        if (exact == null) {
            ResourceObject inherited = resourceManager.getResource(resourceType, resourceId);
            if (inherited == null) {
                warnMissing(resourceType, resourceId);
                return null;
            }
            return castOrWarn(resourceType, resourceId, inherited, expectedType);
        }
        return castOrWarn(resourceType, resourceId, exact, expectedType);
    }

    private <T extends ResourceObject> T castOrWarn(
            KeyId resourceType,
            KeyId resourceId,
            ResourceObject resourceObject,
            Class<T> expectedType) {
        if (expectedType.isInstance(resourceObject)) {
            return expectedType.cast(resourceObject);
        }
        warnIncompatible(resourceType, resourceId, resourceObject);
        return null;
    }

    private void warnIncompatible(KeyId resourceType, KeyId resourceId, ResourceObject resourceObject) {
        String key = resourceType + ":" + resourceId + ":" + resourceObject.getClass().getName();
        if (!warnedIncompatible.add(key)) {
            return;
        }
        SketchDiagnostics.get().warn(
                DIAG_MODULE,
                "Resource " + resourceType + ":" + resourceId
                        + " resolved to incompatible object " + resourceObject.getClass().getName()
                        + " for Vulkan backend-native descriptor write");
    }

    private void warnMissing(KeyId resourceType, KeyId resourceId) {
        String key = resourceType + ":" + resourceId;
        if (!warnedMissing.add(key)) {
            return;
        }
        SketchDiagnostics.get().warn(
                DIAG_MODULE,
                "No Vulkan backend-native resource resolved for " + resourceType + ":" + resourceId);
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

    @Override
    public BackendInstalledBindableResource resolveBindableResource(KeyId resourceType, KeyId resourceId) {
        KeyId normalizedType = ResourceTypes.normalize(resourceType);
        if (ResourceTypes.TEXTURE.equals(normalizedType) || ResourceTypes.IMAGE.equals(normalizedType)) {
            BackendInstalledTexture textureResource = resolveTexture(resourceId);
            return textureResource instanceof BackendInstalledBindableResource bindable ? bindable : null;
        }
        BackendInstalledBuffer buffer = resolveBuffer(normalizedType, resourceId);
        return buffer instanceof BackendInstalledBindableResource bindable ? bindable : null;
    }

    @Override
    public BackendInstalledTexture resolveTexture(KeyId resourceId) {
        return resolveTextureResource(resourceId);
    }

    @Override
    public BackendInstalledRenderTarget resolveRenderTarget(KeyId renderTargetId) {
        return null;
    }

    @Override
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
}

