package rogo.sketch.core.backend;

import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.resource.ResourceAccess;
import rogo.sketch.core.resource.ResourceViewRole;

/**
 * Installed resource that exposes a backend binding operation.
 */
public interface BackendInstalledBindableResource extends BackendInstalledResource {
    void bind(KeyId resourceType, int binding);

    default void bind(KeyId resourceType, int binding, ResourceViewRole viewRole, ResourceAccess access) {
        bind(resourceType, binding);
    }
}

