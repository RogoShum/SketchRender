package rogo.sketch.core.backend;

import rogo.sketch.core.api.Resizeable;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;

/**
 * Backend-owned storage buffer resource with host-write upload helpers.
 */
public interface BackendStorageBuffer extends BackendInstalledBuffer, BackendInstalledBindableResource, Resizeable {
    ResolvedBufferResource descriptor();

    long dataCount();

    long capacityBytes();

    long strideBytes();

    long memoryAddress();

    long position();

    void position(long newPosition);

    void upload();

    void upload(long sourceAddress, long byteCount);

    void upload(long elementIndex);

    void upload(long elementIndex, int byteCount);

    void resetUpload(BufferUpdatePolicy updatePolicy);

    void ensureCapacity(int requiredCount, boolean copy);

    void ensureCapacity(int requiredCount, boolean copy, boolean force);

    void setBufferPointer(long bufferPointer);

    void setCapacity(long capacityBytes);
}

