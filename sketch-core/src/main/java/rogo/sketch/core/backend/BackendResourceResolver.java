package rogo.sketch.core.backend;

import rogo.sketch.core.util.KeyId;

/**
 * Backend-owned resolver for installed resources.
 * <p>
 * Core render flow and binding plans should resolve live GPU resources through
 * this interface instead of directly casting authoring resources from the
 * resource manager.
 * </p>
 */
public interface BackendResourceResolver {
    BackendResourceResolver NO_OP = new BackendResourceResolver() {
    };

    default BackendInstalledBindableResource resolveBindableResource(KeyId resourceType, KeyId resourceId) {
        return null;
    }

    default BackendInstalledTexture resolveTexture(KeyId resourceId) {
        return null;
    }

    default BackendInstalledRenderTarget resolveRenderTarget(KeyId renderTargetId) {
        return null;
    }

    default BackendInstalledBuffer resolveBuffer(KeyId resourceType, KeyId resourceId) {
        return null;
    }
}

