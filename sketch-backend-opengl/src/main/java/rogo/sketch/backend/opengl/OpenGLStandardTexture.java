package rogo.sketch.backend.opengl;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL42;
import rogo.sketch.core.backend.BackendInstalledBindableResource;
import rogo.sketch.core.backend.BackendInstalledTexture;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.backend.opengl.resource.descriptor.OpenGLImageFormatMappings;
import rogo.sketch.backend.opengl.resource.descriptor.OpenGLSamplerMappings;
import rogo.sketch.core.resource.vision.StandardTexture;
import rogo.sketch.core.util.KeyId;

public final class OpenGLStandardTexture extends StandardTexture
        implements BackendInstalledTexture, BackendInstalledBindableResource, OpenGLTextureHandleResource {
    private final GraphicsAPI api;

    public OpenGLStandardTexture(
            int handle,
            KeyId keyId,
            ResolvedImageResource descriptor,
            @Nullable String imagePath,
            GraphicsAPI api) {
        super(handle, keyId, descriptor, imagePath);
        this.api = api;
    }

    @Override
    public void bind(KeyId resourceType, int textureUnit) {
        if (isDisposed()) {
            throw new IllegalStateException("Texture has been disposed");
        }

        int handle = this.handle;
        if (ResourceTypes.normalize(resourceType).equals(ResourceTypes.IMAGE)) {
            api.getTextureStrategy().bindImageTexture(
                    textureUnit,
                    handle,
                    0,
                    false,
                    0,
                    GL42.GL_READ_WRITE,
                    OpenGLImageFormatMappings.resolve(descriptor().format()).imageUnitFormat());
        } else {
            api.getTextureStrategy().bindTextureUnit(textureUnit, handle);
        }

        api.getTextureStrategy().texParameteri(handle, GL11.GL_TEXTURE_MIN_FILTER, OpenGLSamplerMappings.toMinFilter(descriptor()));
        api.getTextureStrategy().texParameteri(handle, GL11.GL_TEXTURE_MAG_FILTER, OpenGLSamplerMappings.toMagFilter(descriptor()));
        api.getTextureStrategy().texParameteri(handle, GL11.GL_TEXTURE_WRAP_S, OpenGLSamplerMappings.toWrap(descriptor().wrapS()));
        api.getTextureStrategy().texParameteri(handle, GL11.GL_TEXTURE_WRAP_T, OpenGLSamplerMappings.toWrap(descriptor().wrapT()));
    }

    @Override
    public int textureHandle() {
        return handle;
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
        api.bindTexture(GL11.GL_TEXTURE_2D, handle);
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
            api.deleteTextures(handle);
            markDisposed();
        }
    }
}

