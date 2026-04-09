package rogo.sketch.core.backend;

/**
 * Backend-owned atomic counter buffer contract.
 */
public interface BackendCounterBuffer extends BackendInstalledBuffer, BackendInstalledBindableResource {
    int handle();

    long counterCount();

    long strideBytes();
}

