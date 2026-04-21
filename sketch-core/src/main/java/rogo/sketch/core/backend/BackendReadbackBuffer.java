package rogo.sketch.core.backend;

import rogo.sketch.core.api.DataResourceObject;

/**
 * Host-visible readback buffer contract shared by terrain/entity readback
 * consumers. Backends may back this with persistent mappings, staging buffers,
 * or any equivalent host-readable allocation.
 */
public interface BackendReadbackBuffer
        extends BackendInstalledBuffer, BackendInstalledBindableResource, DataResourceObject {
    void ensureCapacity(int requiredCount, boolean force);

    int getInt(long index);

    int getUnsignedByte(long index);

    byte getByte(long index);
}