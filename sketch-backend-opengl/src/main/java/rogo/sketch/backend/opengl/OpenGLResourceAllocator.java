package rogo.sketch.backend.opengl;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.backend.opengl.internal.IGLTextureStrategy;
import rogo.sketch.backend.opengl.resource.descriptor.OpenGLImageFormatMapping;
import rogo.sketch.backend.opengl.resource.descriptor.OpenGLImageFormatMappings;
import rogo.sketch.backend.opengl.resource.descriptor.OpenGLSamplerMappings;
import rogo.sketch.backend.opengl.util.GLFeatureChecker;
import rogo.sketch.core.backend.BackendCounterBuffer;
import rogo.sketch.core.backend.BackendIndirectBuffer;
import rogo.sketch.core.backend.BackendInstalledBindableResource;
import rogo.sketch.core.backend.BackendInstalledBuffer;
import rogo.sketch.core.backend.BackendInstalledRenderTarget;
import rogo.sketch.core.backend.BackendInstalledTexture;
import rogo.sketch.core.backend.BackendReadbackBuffer;
import rogo.sketch.core.backend.BackendStorageBuffer;
import rogo.sketch.core.backend.BackendUniformBuffer;
import rogo.sketch.core.backend.LogicalResourceRegistryBinder;
import rogo.sketch.core.backend.ResourceAllocator;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.memory.TrackedTransientAllocation;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.descriptor.BufferRole;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.resource.descriptor.ResolvedRenderTargetSpec;
import rogo.sketch.core.resource.vision.RenderTarget;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.util.KeyId;

import java.nio.ByteBuffer;

final class OpenGLResourceAllocator implements ResourceAllocator, LogicalResourceRegistryBinder {
    private final GraphicsAPI api;
    private final OpenGLBackendResourceResolver resourceResolver;

    OpenGLResourceAllocator(GraphicsAPI api, OpenGLBackendResourceResolver resourceResolver) {
        this.api = api;
        this.resourceResolver = resourceResolver;
    }

    @Override
    public Texture installTexture(
            KeyId resourceId,
            ResolvedImageResource descriptor,
            @Nullable String imagePath,
            @Nullable ByteBuffer imageData) {
        IGLTextureStrategy strategy = api.getTextureStrategy();
        OpenGLImageFormatMapping formatMapping = OpenGLImageFormatMappings.resolve(descriptor.format());
        int handle = strategy.createTexture(GL11.GL_TEXTURE_2D);

        strategy.texParameteri(handle, GL11.GL_TEXTURE_MIN_FILTER, OpenGLSamplerMappings.toMinFilter(descriptor));
        strategy.texParameteri(handle, GL11.GL_TEXTURE_MAG_FILTER, OpenGLSamplerMappings.toMagFilter(descriptor));
        strategy.texParameteri(handle, GL11.GL_TEXTURE_WRAP_S, OpenGLSamplerMappings.toWrap(descriptor.wrapS()));
        strategy.texParameteri(handle, GL11.GL_TEXTURE_WRAP_T, OpenGLSamplerMappings.toWrap(descriptor.wrapT()));

        int width = descriptor.width();
        int height = descriptor.height();
        strategy.texImage2D(
                handle,
                0,
                formatMapping.internalFormat(),
                width,
                height,
                formatMapping.uploadFormat(),
                formatMapping.uploadType(),
                imageData);

        if (descriptor.usesMipmaps()) {
            strategy.generateMipmap(handle);
        }

        OpenGLStandardTexture texture = new OpenGLStandardTexture(handle, resourceId, descriptor, imagePath, api);
        texture.updateCurrentSize(width, height);
        return texture;
    }

    @Override
    public RenderTarget installRenderTarget(KeyId resourceId, ResolvedRenderTargetSpec descriptor) {
        int handle = api.createFramebuffer();
        OpenGLStandardRenderTarget renderTarget = new OpenGLStandardRenderTarget(handle, resourceId, descriptor, api, this);
        api.bindFrameBuffer(handle);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("Framebuffer not complete: " + status + " for " + resourceId);
        }
        api.bindFrameBuffer(0);
        return renderTarget;
    }

    @Override
    public BackendUniformBuffer installUniformBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData) {
        return new OpenGLUniformBufferResource(api, descriptor, initialData);
    }

    @Override
    public BackendStorageBuffer installStorageBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData) {
        OpenGLStorageBuffer buffer = new OpenGLStorageBuffer(
                Math.max(1L, descriptor.elementCount()),
                Math.max(1L, descriptor.strideBytes()),
                toGLUsage(descriptor.updatePolicy()));
        if (initialData != null && initialData.remaining() > 0) {
            try (TrackedTransientAllocation copy = allocateTransient(
                    "gl-storage-buffer-init/" + resourceId,
                    initialData.remaining())) {
                copy.buffer().put(initialData.slice()).flip();
                buffer.upload(copy.address(), copy.buffer().remaining());
            }
        }
        return buffer;
    }

    @Override
    public BackendCounterBuffer installCounterBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData) {
        OpenGLCounterStorageBuffer buffer = new OpenGLCounterStorageBuffer(resolveCounterValueType(descriptor));
        if (initialData != null && initialData.remaining() >= Integer.BYTES) {
            buffer.updateCount(initialData.slice().getInt(0));
        }
        return buffer;
    }

    @Override
    public BackendIndirectBuffer installIndirectBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            long commandCapacity) {
        long capacity = commandCapacity > 0L
                ? commandCapacity
                : Math.max(1L, descriptor.capacityBytes() / Math.max(1L, descriptor.strideBytes()));
        return new OpenGLIndirectBuffer(capacity);
    }

    @Override
    public BackendReadbackBuffer installReadbackBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            int initialElementCapacity) {
        long elementCount = descriptor != null ? Math.max(1L, descriptor.elementCount()) : 1L;
        long strideBytes = descriptor != null ? Math.max(1L, descriptor.strideBytes()) : Integer.BYTES;
        if (!GLFeatureChecker.supportsPersistentMapping()) {
            OpenGLReadbackStorageBuffer buffer = new OpenGLReadbackStorageBuffer(
                    Math.max(elementCount, initialElementCapacity),
                    strideBytes);
            if (initialElementCapacity > 0) {
                buffer.ensureCapacity(initialElementCapacity, false);
            }
            return buffer;
        }
        OpenGLPersistentReadStorageBuffer buffer = new OpenGLPersistentReadStorageBuffer(
                Math.max(elementCount, initialElementCapacity),
                strideBytes);
        if (initialElementCapacity > 0) {
            buffer.ensureCapacity(initialElementCapacity, false);
        }
        return buffer;
    }

    @Override
    public <C extends RenderContext> boolean installExecutionPlan(
            GraphicsPipeline<C> pipeline,
            FrameExecutionPlan plan,
            long frameEpoch,
            int framesInFlight,
            boolean uploadGeometryData) {
        OpenGLGeometryMaterializer.installExecutionGeometryBindings(pipeline, plan, uploadGeometryData);
        return true;
    }

    @Override
    public BackendInstalledBindableResource resolveBindableResource(KeyId resourceType, KeyId resourceId) {
        return resourceResolver.resolveBindableResource(resourceType, resourceId);
    }

    @Override
    public BackendInstalledTexture resolveTexture(KeyId resourceId) {
        return resourceResolver.resolveTexture(resourceId);
    }

    @Override
    public BackendInstalledRenderTarget resolveRenderTarget(KeyId renderTargetId) {
        return resourceResolver.resolveRenderTarget(renderTargetId);
    }

    @Override
    public BackendInstalledBuffer resolveBuffer(KeyId resourceType, KeyId resourceId) {
        return resourceResolver.resolveBuffer(resourceType, resourceId);
    }

    @Override
    public ResourceObject resolveLogicalResource(KeyId resourceType, KeyId resourceId) {
        return resourceResolver.resolveLogicalResource(resourceType, resourceId);
    }

    @Override
    public ResourceObject resolveLogicalResourceExact(KeyId resourceType, KeyId resourceId) {
        return resourceResolver.resolveLogicalResourceExact(resourceType, resourceId);
    }

    @Override
    public void bindLogicalResourceRegistry(GraphicsResourceManager resourceManager) {
        resourceResolver.bindLogicalResourceManager(resourceManager);
    }

    private static int toGLUsage(BufferUpdatePolicy updatePolicy) {
        if (updatePolicy == null) {
            return GL15.GL_DYNAMIC_DRAW;
        }
        return switch (updatePolicy) {
            case IMMUTABLE -> GL15.GL_STATIC_DRAW;
            case DYNAMIC -> GL15.GL_DYNAMIC_DRAW;
            case STREAM -> GL15.GL_STREAM_DRAW;
        };
    }

    private static rogo.sketch.core.data.type.ValueType resolveCounterValueType(ResolvedBufferResource descriptor) {
        if (descriptor == null || descriptor.role() != BufferRole.ATOMIC_COUNTER) {
            return rogo.sketch.core.data.type.ValueType.INT;
        }
        return rogo.sketch.core.data.type.ValueType.INT;
    }
}
