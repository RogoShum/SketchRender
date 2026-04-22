package rogo.sketch.core.backend;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.resource.descriptor.ResolvedRenderTargetSpec;
import rogo.sketch.core.resource.vision.RenderTarget;
import rogo.sketch.core.resource.vision.StandardRenderTarget;
import rogo.sketch.core.resource.vision.StandardTexture;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.util.KeyId;

import java.nio.ByteBuffer;

public interface BackendResourceRegistry {
    BackendResourceRegistry NO_OP = new BackendResourceRegistry() {
        @Override
        public Texture installTexture(
                KeyId resourceId,
                ResolvedImageResource descriptor,
                @Nullable String imagePath,
                @Nullable ByteBuffer imageData) {
            StandardTexture texture = new StandardTexture(GpuHandle.NONE, resourceId, descriptor, imagePath);
            texture.updateCurrentSize(descriptor.width(), descriptor.height());
            return texture;
        }

        @Override
        public RenderTarget installRenderTarget(KeyId resourceId, ResolvedRenderTargetSpec descriptor) {
            return new StandardRenderTarget(GpuHandle.NONE, resourceId, descriptor, null);
        }

        @Override
        public BackendUniformBuffer installUniformBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                @Nullable ByteBuffer initialData) {
            return null;
        }

        @Override
        public BackendStorageBuffer installStorageBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                @Nullable ByteBuffer initialData) {
            return null;
        }

        @Override
        public BackendCounterBuffer installCounterBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                @Nullable ByteBuffer initialData) {
            return null;
        }

        @Override
        public BackendIndirectBuffer installIndirectBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                long commandCapacity) {
            return null;
        }

        @Override
        public BackendReadbackBuffer installReadbackBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                int initialElementCapacity) {
            return null;
        }
    };

    Texture installTexture(
            KeyId resourceId,
            ResolvedImageResource descriptor,
            @Nullable String imagePath,
            @Nullable ByteBuffer imageData);

    RenderTarget installRenderTarget(KeyId resourceId, ResolvedRenderTargetSpec descriptor);

    BackendUniformBuffer installUniformBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData);

    BackendStorageBuffer installStorageBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData);

    BackendCounterBuffer installCounterBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData);

    BackendIndirectBuffer installIndirectBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            long commandCapacity);

    BackendReadbackBuffer installReadbackBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            int initialElementCapacity);

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

    default ResourceObject resolveLogicalResource(KeyId resourceType, KeyId resourceId) {
        return null;
    }

    default ResourceObject resolveLogicalResourceExact(KeyId resourceType, KeyId resourceId) {
        return null;
    }

    default void uninstall(KeyId resourceType, KeyId resourceId) {
    }

    default void installExecutionPlan(FrameExecutionPlan plan, long frameEpoch, int framesInFlight) {
    }

    default void shutdown() {
    }
}
