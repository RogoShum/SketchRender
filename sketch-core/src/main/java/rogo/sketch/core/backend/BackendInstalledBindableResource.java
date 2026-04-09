package rogo.sketch.core.backend;

import rogo.sketch.core.util.KeyId;

/**
 * Installed resource that exposes a backend binding operation.
 */
public interface BackendInstalledBindableResource extends BackendInstalledResource {
    void bind(KeyId resourceType, int binding);
}

