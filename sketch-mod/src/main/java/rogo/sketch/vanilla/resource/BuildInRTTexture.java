package rogo.sketch.vanilla.resource;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL42;
import rogo.sketch.backend.opengl.OpenGLTextureHandleResource;
import rogo.sketch.core.backend.BackendInstalledBindableResource;
import rogo.sketch.core.backend.BackendInstalledTexture;
import rogo.sketch.backend.opengl.internal.OpenGLRuntimeSupport;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.descriptor.ImageFormat;
import rogo.sketch.core.resource.descriptor.ImageUsage;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.resource.descriptor.SamplerFilter;
import rogo.sketch.core.resource.descriptor.SamplerWrap;
import rogo.sketch.backend.opengl.resource.descriptor.OpenGLImageFormatMappings;
import rogo.sketch.backend.opengl.resource.descriptor.OpenGLSamplerMappings;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.util.KeyId;

import java.util.Set;
import java.util.function.Supplier;

public class BuildInRTTexture extends Texture
        implements BackendInstalledTexture, BackendInstalledBindableResource, OpenGLTextureHandleResource {
    private final Supplier<RenderTarget> renderTarget;
    private final boolean depth;

    public BuildInRTTexture(
            KeyId keyId,
            Supplier<RenderTarget> renderTarget,
            ImageFormat format,
            Set<ImageUsage> usages,
            boolean depth,
            SamplerFilter minFilter,
            SamplerFilter magFilter,
            SamplerWrap wrapS,
            SamplerWrap wrapT) {
        super(-1, keyId, new ResolvedImageResource(keyId, 1, 1, 1, format, usages, minFilter, magFilter, null, wrapS, wrapT, null));
        this.renderTarget = renderTarget;
        this.depth = depth;
    }

    private int resolveHandle() {
        return depth ? renderTarget.get().getDepthTextureId() : renderTarget.get().getColorTextureId();
    }

    @Override
    public int getCurrentWidth() {
        return renderTarget.get().width;
    }

    @Override
    public int getCurrentHeight() {
        return renderTarget.get().height;
    }

    @Override
    public void bind(KeyId resourceType, int textureUnit) {
        if (isDisposed()) {
            throw new IllegalStateException("Texture has been disposed");
        }

        int handle = resolveHandle();
        if (ResourceTypes.normalize(resourceType).equals(ResourceTypes.IMAGE)) {
            GL42.glBindImageTexture(
                    textureUnit,
                    handle,
                    0,
                    false,
                    0,
                    GL42.GL_READ_WRITE,
                    OpenGLImageFormatMappings.resolve(descriptor().format()).imageUnitFormat());
        } else {
            OpenGLRuntimeSupport.textureStrategy().bindTextureUnit(textureUnit, handle);
        }

        OpenGLRuntimeSupport.textureStrategy().texParameteri(
                handle,
                GL11.GL_TEXTURE_MIN_FILTER,
                OpenGLSamplerMappings.toMinFilter(descriptor()));
        OpenGLRuntimeSupport.textureStrategy().texParameteri(
                handle,
                GL11.GL_TEXTURE_MAG_FILTER,
                OpenGLSamplerMappings.toMagFilter(descriptor()));
        OpenGLRuntimeSupport.textureStrategy().texParameteri(
                handle,
                GL11.GL_TEXTURE_WRAP_S,
                OpenGLSamplerMappings.toWrap(descriptor().wrapS()));
        OpenGLRuntimeSupport.textureStrategy().texParameteri(
                handle,
                GL11.GL_TEXTURE_WRAP_T,
                OpenGLSamplerMappings.toWrap(descriptor().wrapT()));
    }

    @Override
    public void dispose() {

    }

    @Override
    public int textureHandle() {
        return resolveHandle();
    }
}

