package rogo.sketch.vanilla.resource;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL42;
import rogo.sketch.backend.opengl.OpenGLTextureHandleResource;
import rogo.sketch.core.backend.BackendInstalledBindableResource;
import rogo.sketch.core.backend.BackendInstalledTexture;
import rogo.sketch.backend.opengl.internal.OpenGLRuntimeSupport;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.backend.opengl.resource.descriptor.OpenGLImageFormatMappings;
import rogo.sketch.backend.opengl.resource.descriptor.OpenGLSamplerMappings;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.util.KeyId;

import javax.annotation.Nullable;

/**
 * Minecraft-specific texture implementation that wraps MC texture resources
 */
public class VanillaTexture extends Texture
        implements BackendInstalledTexture, BackendInstalledBindableResource, OpenGLTextureHandleResource {
    protected final ResourceLocation resourceLocation;
    protected final AbstractTexture texture;

    /**
     * Create a Minecraft-compatible texture
     */
    public VanillaTexture(KeyId keyId, ResourceLocation resourceLocation, AbstractTexture texture, ResolvedImageResource descriptor) {
        // MC textures IDs are managed by MC
        super(texture.getId(), keyId, descriptor);
        this.resourceLocation = resourceLocation;
        this.texture = texture;
    }

    @Override
    public void bind(KeyId resourceType, int textureUnit) {
        if (isDisposed()) {
            throw new IllegalStateException("Texture has been disposed");
        }

        if (ResourceTypes.normalize(resourceType).equals(ResourceTypes.IMAGE)) {
            int glHandle = handle.asGlName();
            GL42.glBindImageTexture(textureUnit, glHandle, 0, false, 0, GL42.GL_READ_WRITE,
                    OpenGLImageFormatMappings.resolve(descriptor.format()).imageUnitFormat());
        } else {
            int glHandle = handle.asGlName();
            OpenGLRuntimeSupport.textureStrategy().bindTextureUnit(textureUnit, glHandle);
            OpenGLRuntimeSupport.textureStrategy().texParameteri(glHandle, org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, OpenGLSamplerMappings.toMinFilter(descriptor));
            OpenGLRuntimeSupport.textureStrategy().texParameteri(glHandle, org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, OpenGLSamplerMappings.toMagFilter(descriptor));
            OpenGLRuntimeSupport.textureStrategy().texParameteri(glHandle, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S, OpenGLSamplerMappings.toWrap(descriptor.wrapS()));
            OpenGLRuntimeSupport.textureStrategy().texParameteri(glHandle, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T, OpenGLSamplerMappings.toWrap(descriptor.wrapT()));
        }
    }

    /**
     * Get the MC resource location
     */
    @Nullable
    public ResourceLocation getResourceLocation() {
        return resourceLocation;
    }

    @Override
    public void dispose() {

    }

    @Override
    public int textureHandle() {
        return handle.asGlName();
    }
}

