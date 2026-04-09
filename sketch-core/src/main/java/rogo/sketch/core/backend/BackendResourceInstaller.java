package rogo.sketch.core.backend;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.resource.descriptor.ResolvedRenderTargetSpec;
import rogo.sketch.core.resource.vision.RenderTarget;
import rogo.sketch.core.resource.vision.StandardRenderTarget;
import rogo.sketch.core.resource.vision.StandardTexture;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.util.KeyId;

import java.nio.ByteBuffer;

/**
 * Backend-owned resource installation hook.
 * <p>
 * Core loaders resolve canonical descriptors first and delegate native resource
 * installation to the active backend through this interface. Backends that do
 * not support native installation for a resource type may return descriptor-only
 * wrappers.
 * </p>
 */
public interface BackendResourceInstaller {
    BackendResourceInstaller NO_OP = new BackendResourceInstaller() {
        @Override
        public Texture createTexture(
                KeyId resourceId,
                ResolvedImageResource descriptor,
                @Nullable String imagePath,
                @Nullable ByteBuffer imageData) {
            StandardTexture texture = new StandardTexture(0, resourceId, descriptor, imagePath);
            texture.updateCurrentSize(descriptor.width(), descriptor.height());
            return texture;
        }

        @Override
        public RenderTarget createRenderTarget(KeyId resourceId, ResolvedRenderTargetSpec descriptor) {
            return new StandardRenderTarget(0, resourceId, descriptor, null);
        }

        @Override
        public BackendUniformBuffer createUniformBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                @Nullable ByteBuffer initialData) {
            return null;
        }

        @Override
        public BackendStorageBuffer createStorageBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                @Nullable ByteBuffer initialData) {
            return null;
        }

        @Override
        public BackendCounterBuffer createCounterBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                @Nullable ByteBuffer initialData) {
            return null;
        }

        @Override
        public BackendIndirectBuffer createIndirectBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                long commandCapacity) {
            return null;
        }
    };

    Texture createTexture(
            KeyId resourceId,
            ResolvedImageResource descriptor,
            @Nullable String imagePath,
            @Nullable ByteBuffer imageData);

    RenderTarget createRenderTarget(KeyId resourceId, ResolvedRenderTargetSpec descriptor);

    BackendUniformBuffer createUniformBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData);

    BackendStorageBuffer createStorageBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData);

    BackendCounterBuffer createCounterBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData);

    BackendIndirectBuffer createIndirectBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            long commandCapacity);
}

