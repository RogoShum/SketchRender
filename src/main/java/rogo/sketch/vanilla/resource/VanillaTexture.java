package rogo.sketch.vanilla.resource;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import rogo.sketch.render.resource.Texture;
import rogo.sketch.util.Identifier;

import javax.annotation.Nullable;

/**
 * Minecraft-specific texture implementation that wraps MC texture resources
 */
public class VanillaTexture extends Texture {
    protected final ResourceLocation resourceLocation;

    /**
     * Create a Minecraft-compatible texture
     */
    public VanillaTexture(Identifier identifier, ResourceLocation resourceLocation, AbstractTexture texture) {
        super(texture.getId(), identifier, GL11.GL_RGBA, GL11.GL_NEAREST, GL11.GL_REPEAT);
        this.resourceLocation = resourceLocation;
    }

    /**
     * MC textures don't need manual resizing
     */
    @Override
    public void resize(int width, int height) {
    }

    /**
     * Get the MC resource location
     */
    @Nullable
    public ResourceLocation getResourceLocation() {
        return resourceLocation;
    }

    /**
     * Don't dispose MC textures - they are managed by MC
     */
    @Override
    public void dispose() {
        disposed = true;
    }
}