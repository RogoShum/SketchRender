package rogo.sketch.backend.opengl;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.backend.BackendCounterBuffer;
import rogo.sketch.core.backend.BackendIndirectBuffer;
import rogo.sketch.core.backend.BackendResourceInstaller;
import rogo.sketch.core.backend.BackendStorageBuffer;
import rogo.sketch.core.backend.BackendUniformBuffer;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.backend.opengl.internal.IGLTextureStrategy;
import rogo.sketch.core.resource.descriptor.BufferUpdatePolicy;
import rogo.sketch.core.resource.descriptor.BufferRole;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.resource.descriptor.ResolvedRenderTargetSpec;
import rogo.sketch.backend.opengl.resource.descriptor.OpenGLImageFormatMapping;
import rogo.sketch.backend.opengl.resource.descriptor.OpenGLImageFormatMappings;
import rogo.sketch.backend.opengl.resource.descriptor.OpenGLSamplerMappings;
import rogo.sketch.core.resource.vision.RenderTarget;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.util.KeyId;

import java.nio.ByteBuffer;

final class OpenGLBackendResourceInstaller implements BackendResourceInstaller {
    private final GraphicsAPI api;
    private final OpenGLBackendResourceResolver resourceResolver;

    OpenGLBackendResourceInstaller(GraphicsAPI api, OpenGLBackendResourceResolver resourceResolver) {
        this.api = api;
        this.resourceResolver = resourceResolver;
    }

    @Override
    public Texture createTexture(
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
        if (imageData != null) {
            strategy.texImage2D(
                    handle,
                    0,
                    formatMapping.internalFormat(),
                    width,
                    height,
                    formatMapping.uploadFormat(),
                    formatMapping.uploadType(),
                    imageData);
        } else {
            strategy.texImage2D(
                    handle,
                    0,
                    formatMapping.internalFormat(),
                    width,
                    height,
                    formatMapping.uploadFormat(),
                    formatMapping.uploadType(),
                    null);
        }

        if (descriptor.usesMipmaps()) {
            strategy.generateMipmap(handle);
        }

        OpenGLStandardTexture texture = new OpenGLStandardTexture(handle, resourceId, descriptor, imagePath, api);
        texture.updateCurrentSize(width, height);
        return texture;
    }

    @Override
    public RenderTarget createRenderTarget(KeyId resourceId, ResolvedRenderTargetSpec descriptor) {
        int handle = api.createFramebuffer();
        OpenGLStandardRenderTarget renderTarget = new OpenGLStandardRenderTarget(handle, resourceId, descriptor, api, resourceResolver);
        api.bindFrameBuffer(handle);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("Framebuffer not complete: " + status + " for " + resourceId);
        }
        api.bindFrameBuffer(0);
        return renderTarget;
    }

    @Override
    public BackendUniformBuffer createUniformBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData) {
        return new OpenGLUniformBufferResource(api, descriptor, initialData);
    }

    @Override
    public BackendStorageBuffer createStorageBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData) {
        OpenGLStorageBuffer buffer = new OpenGLStorageBuffer(
                Math.max(1L, descriptor.elementCount()),
                Math.max(1L, descriptor.strideBytes()),
                toGLUsage(descriptor.updatePolicy()));
        if (initialData != null && initialData.remaining() > 0) {
            ByteBuffer copy = MemoryUtil.memAlloc(initialData.remaining());
            try {
                copy.put(initialData.slice()).flip();
                buffer.upload(MemoryUtil.memAddress(copy), copy.remaining());
            } finally {
                MemoryUtil.memFree(copy);
            }
        }
        return buffer;
    }

    @Override
    public BackendCounterBuffer createCounterBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            @Nullable ByteBuffer initialData) {
        OpenGLCounterBuffer buffer = new OpenGLCounterBuffer(resolveCounterValueType(descriptor));
        if (initialData != null && initialData.remaining() >= Integer.BYTES) {
            buffer.updateCount(initialData.slice().getInt(0));
        }
        return buffer;
    }

    @Override
    public BackendIndirectBuffer createIndirectBuffer(
            KeyId resourceId,
            ResolvedBufferResource descriptor,
            long commandCapacity) {
        long capacity = commandCapacity > 0L
                ? commandCapacity
                : Math.max(1L, descriptor.capacityBytes() / Math.max(1L, descriptor.strideBytes()));
        return new OpenGLIndirectBuffer(capacity);
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

