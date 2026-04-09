package rogo.sketch.backend.opengl;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.backend.BackendInstalledBindableResource;
import rogo.sketch.core.backend.BackendInstalledBuffer;
import rogo.sketch.core.backend.BackendInstalledRenderTarget;
import rogo.sketch.core.backend.BackendInstalledTexture;
import rogo.sketch.core.backend.BackendResourceResolver;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;

final class OpenGLBackendResourceResolver implements BackendResourceResolver {
    private final GraphicsResourceManager resourceManager = GraphicsResourceManager.getInstance();

    @Override
    public BackendInstalledBindableResource resolveBindableResource(KeyId resourceType, KeyId resourceId) {
        KeyId normalizedType = ResourceTypes.normalize(resourceType);
        if (ResourceTypes.TEXTURE.equals(normalizedType) || ResourceTypes.IMAGE.equals(normalizedType)) {
            BackendInstalledTexture texture = resolveTexture(resourceId);
            return texture instanceof BackendInstalledBindableResource bindable ? bindable : null;
        }
        BackendInstalledBuffer buffer = resolveBuffer(normalizedType, resourceId);
        return buffer instanceof BackendInstalledBindableResource bindable ? bindable : null;
    }

    @Override
    public BackendInstalledTexture resolveTexture(KeyId resourceId) {
        return resolve(ResourceTypes.TEXTURE, resourceId, BackendInstalledTexture.class);
    }

    @Override
    public BackendInstalledRenderTarget resolveRenderTarget(KeyId renderTargetId) {
        return resolve(ResourceTypes.RENDER_TARGET, renderTargetId, BackendInstalledRenderTarget.class);
    }

    @Override
    public BackendInstalledBuffer resolveBuffer(KeyId resourceType, KeyId resourceId) {
        return resolve(resourceType, resourceId, BackendInstalledBuffer.class);
    }

    private <T> T resolve(KeyId resourceType, KeyId resourceId, Class<T> expectedType) {
        if (resourceId == null) {
            return null;
        }
        ResourceObject exact = resourceManager.getResourceExact(resourceType, resourceId);
        if (expectedType.isInstance(exact)) {
            return expectedType.cast(exact);
        }
        ResourceObject inherited = resourceManager.getResource(resourceType, resourceId);
        if (expectedType.isInstance(inherited)) {
            return expectedType.cast(inherited);
        }
        return null;
    }
}

