package rogo.sketch.vanilla.resource;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL42;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.Texture;
import rogo.sketch.core.util.KeyId;

import javax.annotation.Nullable;

/**
 * Minecraft-specific texture implementation that wraps MC texture resources
 */
public class VanillaTexture extends Texture {
    protected final ResourceLocation resourceLocation;
    protected final AbstractTexture texture;

    /**
     * Create a Minecraft-compatible texture
     */
    public VanillaTexture(KeyId keyId, ResourceLocation resourceLocation, AbstractTexture texture, int width, int height, int minFilter, int magFilter, int wrapS, int wrapT) {
        // MC textures IDs are managed by MC
        super(texture.getId(), keyId, width, height, GL11.GL_RGBA, minFilter, magFilter, wrapS, wrapT);
        this.resourceLocation = resourceLocation;
        this.texture = texture;
    }

    @Override
    public void bind(KeyId resourceType, int textureUnit) {
        if (isDisposed()) {
            throw new IllegalStateException("Texture has been disposed");
        }

        if (resourceType.equals(ResourceTypes.IMAGE_BUFFER)) {
            GL42.glBindImageTexture(textureUnit, handle, 0, false, 0, GL42.GL_READ_WRITE, GL11.GL_RGBA);
        } else {
            GraphicsDriver.getCurrentAPI().getTextureStrategy().bindTextureUnit(textureUnit, handle);
            GraphicsDriver.getCurrentAPI().textureParameteri(textureUnit, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
            GraphicsDriver.getCurrentAPI().textureParameteri(textureUnit, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
            GraphicsDriver.getCurrentAPI().textureParameteri(textureUnit, GL11.GL_TEXTURE_WRAP_S, wrapS);
            GraphicsDriver.getCurrentAPI().textureParameteri(textureUnit, GL11.GL_TEXTURE_WRAP_T, wrapT);
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
}