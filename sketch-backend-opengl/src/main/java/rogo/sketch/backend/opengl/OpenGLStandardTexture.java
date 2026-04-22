package rogo.sketch.backend.opengl;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL42;
import rogo.sketch.core.backend.BackendInstalledBindableResource;
import rogo.sketch.core.backend.BackendInstalledTexture;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.core.resource.ResourceAccess;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.ResourceViewRole;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.backend.opengl.resource.descriptor.OpenGLImageFormatMappings;
import rogo.sketch.backend.opengl.resource.descriptor.OpenGLSamplerMappings;
import rogo.sketch.core.memory.ImageMemoryEstimator;
import rogo.sketch.core.memory.MemoryDomain;
import rogo.sketch.core.memory.MemoryLease;
import rogo.sketch.core.memory.UnifiedMemoryFabric;
import rogo.sketch.core.resource.vision.StandardTexture;
import rogo.sketch.core.util.KeyId;

public final class OpenGLStandardTexture extends StandardTexture
        implements BackendInstalledTexture, BackendInstalledBindableResource, OpenGLTextureHandleResource {
    private final GraphicsAPI api;
    private final MemoryLease textureLease;

    public OpenGLStandardTexture(
            int handle,
            KeyId keyId,
            ResolvedImageResource descriptor,
            @Nullable String imagePath,
            GraphicsAPI api) {
        super(handle, keyId, descriptor, imagePath);
        this.api = api;
        this.textureLease = UnifiedMemoryFabric.get()
                .openLease(MemoryDomain.GPU_TEXTURE, "gl-texture/" + keyId)
                .bindSuppliers(this::trackedReservedBytes, this::trackedLiveBytes);
    }

    @Override
    public void bind(KeyId resourceType, int textureUnit) {
        bind(resourceType, textureUnit, ResourceViewRole.defaultForResourceType(resourceType), null);
    }

    @Override
    public void bind(KeyId resourceType, int textureUnit, ResourceViewRole viewRole, ResourceAccess access) {
        if (isDisposed()) {
            throw new IllegalStateException("Texture has been disposed");
        }

        int handle = this.handle.asGlName();
        ResourceViewRole resolvedRole = viewRole != null ? viewRole : ResourceViewRole.defaultForResourceType(resourceType);
        ResourceAccess resolvedAccess = access != null ? access : ResourceViewRole.defaultAccessFor(resolvedRole);
        if (ResourceTypes.normalize(resourceType).equals(ResourceTypes.IMAGE)
                || resolvedRole == ResourceViewRole.STORAGE_IMAGE) {
            api.getTextureStrategy().bindImageTexture(
                    textureUnit,
                    handle,
                    0,
                    false,
                    0,
                    toGlImageAccess(resolvedAccess),
                    OpenGLImageFormatMappings.resolve(descriptor().format()).imageUnitFormat());
        } else {
            api.getTextureStrategy().bindTextureUnit(textureUnit, handle);
        }

        api.getTextureStrategy().texParameteri(handle, GL11.GL_TEXTURE_MIN_FILTER, OpenGLSamplerMappings.toMinFilter(descriptor()));
        api.getTextureStrategy().texParameteri(handle, GL11.GL_TEXTURE_MAG_FILTER, OpenGLSamplerMappings.toMagFilter(descriptor()));
        api.getTextureStrategy().texParameteri(handle, GL11.GL_TEXTURE_WRAP_S, OpenGLSamplerMappings.toWrap(descriptor().wrapS()));
        api.getTextureStrategy().texParameteri(handle, GL11.GL_TEXTURE_WRAP_T, OpenGLSamplerMappings.toWrap(descriptor().wrapT()));
    }

    private static int toGlImageAccess(ResourceAccess access) {
        if (access == null) {
            return GL42.GL_READ_WRITE;
        }
        return switch (access) {
            case READ -> GL42.GL_READ_ONLY;
            case WRITE -> GL42.GL_WRITE_ONLY;
            case READ_WRITE -> GL42.GL_READ_WRITE;
        };
    }

    @Override
    public int textureHandle() {
        return handle.asGlName();
    }

    @Override
    public void resize(int width, int height) {
        if (isDisposed()) {
            throw new IllegalStateException("Texture has been disposed");
        }
        if (width == getCurrentWidth() && height == getCurrentHeight()) {
            return;
        }

        var formatMapping = OpenGLImageFormatMappings.resolve(descriptor().format());
        int glHandle = handle.asGlName();
        api.bindTexture(GL11.GL_TEXTURE_2D, glHandle);
        api.texImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                formatMapping.internalFormat(),
                width,
                height,
                0,
                formatMapping.uploadFormat(),
                formatMapping.uploadType(),
                null);
        api.bindTexture(GL11.GL_TEXTURE_2D, 0);
        updateCurrentSize(width, height);
    }

    @Override
    public void dispose() {
        if (!isDisposed()) {
            textureLease.close();
            api.deleteTextures(handle.asGlName());
            markDisposed();
        }
    }

    private long trackedReservedBytes() {
        return isDisposed() ? 0L : trackedLiveBytes();
    }

    private long trackedLiveBytes() {
        if (isDisposed()) {
            return 0L;
        }
        return ImageMemoryEstimator.estimateBytes(
                Math.max(1, getCurrentWidth()),
                Math.max(1, getCurrentHeight()),
                Math.max(1, descriptor().mipLevels()),
                descriptor().format());
    }
}

