package rogo.sketch.backend.opengl;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.backend.BackendInstalledBindableResource;
import rogo.sketch.core.backend.BackendInstalledBuffer;
import rogo.sketch.core.backend.BackendInstalledRenderTarget;
import rogo.sketch.core.backend.BackendInstalledTexture;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;

final class OpenGLBackendResourceResolver {
    private volatile GraphicsResourceManager resourceManager;

    void bindLogicalResourceManager(GraphicsResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public BackendInstalledBindableResource resolveBindableResource(KeyId resourceType, KeyId resourceId) {
        KeyId normalizedType = ResourceTypes.normalize(resourceType);
        if (ResourceTypes.TEXTURE.equals(normalizedType) || ResourceTypes.IMAGE.equals(normalizedType)) {
            BackendInstalledTexture texture = resolveTexture(resourceId);
            return texture instanceof BackendInstalledBindableResource bindable ? bindable : null;
        }
        BackendInstalledBuffer buffer = resolveBuffer(normalizedType, resourceId);
        return buffer instanceof BackendInstalledBindableResource bindable ? bindable : null;
    }

    public BackendInstalledTexture resolveTexture(KeyId resourceId) {
        return resolve(ResourceTypes.TEXTURE, resourceId, BackendInstalledTexture.class);
    }

    public BackendInstalledRenderTarget resolveRenderTarget(KeyId renderTargetId) {
        return resolve(ResourceTypes.RENDER_TARGET, renderTargetId, BackendInstalledRenderTarget.class);
    }

    public BackendInstalledBuffer resolveBuffer(KeyId resourceType, KeyId resourceId) {
        return resolve(resourceType, resourceId, BackendInstalledBuffer.class);
    }

    private <T> T resolve(KeyId resourceType, KeyId resourceId, Class<T> expectedType) {
        GraphicsResourceManager manager = resourceManager;
        if (manager == null || resourceId == null) {
            return null;
        }
        ResourceObject exact = manager.getResourceExact(resourceType, resourceId);
        if (expectedType.isInstance(exact)) {
            return expectedType.cast(exact);
        }
        ResourceObject inherited = manager.getResource(resourceType, resourceId);
        if (expectedType.isInstance(inherited)) {
            return expectedType.cast(inherited);
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

