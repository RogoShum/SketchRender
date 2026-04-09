package rogo.sketch.core.backend;

import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;

import java.nio.ByteBuffer;

/**
 * Backend-owned uniform buffer resource.
 */
public interface BackendUniformBuffer extends BackendInstalledBuffer, BackendInstalledBindableResource {
    ResolvedBufferResource descriptor();

    long sizeBytes();

    void update(ByteBuffer source);

    default void update(byte[] bytes) {
        update(bytes != null ? ByteBuffer.wrap(bytes) : null);
    }
}

