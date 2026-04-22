package rogo.sketch.core.backend;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.util.KeyId;

import java.nio.ByteBuffer;

/**
 * Small helper for creating backend-owned buffer resources from core code.
 */
public final class BackendBufferFactory {
    private BackendBufferFactory() {
    }

    public static BackendUniformBuffer createUniformBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData) {
        BackendUniformBuffer buffer = GraphicsDriver.resourceAllocator()
                .installUniformBuffer(resourceId, descriptor, initialData);
        if (buffer == null) {
            throw new IllegalStateException("Current backend did not install a uniform buffer for " + resourceId);
        }
        return buffer;
    }

    public static BackendStorageBuffer createStorageBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData) {
        BackendStorageBuffer buffer = GraphicsDriver.resourceAllocator()
                .installStorageBuffer(resourceId, descriptor, initialData);
        if (buffer == null) {
            throw new IllegalStateException("Current backend did not install a storage buffer for " + resourceId);
        }
        return buffer;
    }

    public static BackendCounterBuffer createCounterBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData) {
        BackendCounterBuffer buffer = GraphicsDriver.resourceAllocator()
                .installCounterBuffer(resourceId, descriptor, initialData);
        if (buffer == null) {
            throw new IllegalStateException("Current backend did not install a counter buffer for " + resourceId);
        }
        return buffer;
    }

    public static BackendIndirectBuffer createIndirectBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            long commandCapacity) {
        BackendIndirectBuffer buffer = GraphicsDriver.resourceAllocator()
                .installIndirectBuffer(resourceId, descriptor, commandCapacity);
        if (buffer == null) {
            throw new IllegalStateException("Current backend did not install an indirect buffer for " + resourceId);
        }
        return buffer;
    }

    public static BackendReadbackBuffer createReadbackBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            int initialElementCapacity) {
        BackendReadbackBuffer buffer = GraphicsDriver.resourceAllocator()
                .installReadbackBuffer(resourceId, descriptor, initialElementCapacity);
        if (buffer == null) {
            throw new IllegalStateException("Current backend did not install a readback buffer for " + resourceId);
        }
        return buffer;
    }
}

