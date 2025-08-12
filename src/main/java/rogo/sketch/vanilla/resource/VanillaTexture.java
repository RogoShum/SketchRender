package rogo.sketch.vanilla.resource;

import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import rogo.sketch.render.resource.Texture;
import rogo.sketch.util.Identifier;

import javax.annotation.Nullable;

/**
 * Minecraft-specific texture implementation that wraps MC texture resources
 */
public class VanillaTexture extends Texture {
    
    @Nullable
    private final ResourceLocation mcResourceLocation;
    private int mcTextureHandle = -1; // Cached MC texture handle
    private boolean mcHandleResolved = false;

    /**
     * Create a Minecraft-compatible texture
     */
    public VanillaTexture(Identifier identifier, ResourceLocation mcResourceLocation) {
        super(0, identifier, GL11.GL_RGBA, GL11.GL_LINEAR, GL11.GL_REPEAT);
        this.mcResourceLocation = mcResourceLocation;
    }

    /**
     * Get the actual OpenGL handle, resolving MC texture if needed
     */
    @Override
    public int getHandle() {
        if (!mcHandleResolved && mcResourceLocation != null) {
            // TODO: Integrate with MC texture system
            // mcTextureHandle = MinecraftTextureManager.getTextureHandle(mcResourceLocation);
            // mcHandleResolved = true;
            
            // For now, throw exception to indicate MC integration is needed
            throw new UnsupportedOperationException("MC texture integration not implemented yet: " + mcResourceLocation);
        }
        return mcTextureHandle > 0 ? mcTextureHandle : super.getHandle();
    }

    /**
     * MC textures don't need manual resizing
     */
    @Override
    public void resize(int width, int height) {
        // MC textures are managed by Minecraft's texture system
        // We just update our dimension tracking
        // The actual texture size is controlled by MC
        // Note: We could call super.resize() if we want to support custom resizing of MC textures
    }

    /**
     * Bind MC texture with proper MC texture manager integration
     */
    @Override
    public void bind(int textureUnit) {
        if (mcResourceLocation != null) {
            // TODO: Use MC texture manager to bind
            // MinecraftTextureManager.bind(mcResourceLocation, textureUnit);
            throw new UnsupportedOperationException("MC texture binding not implemented yet: " + mcResourceLocation);
        } else {
            super.bind(textureUnit);
        }
    }

    /**
     * Check if this is a MC texture
     */
    public boolean isMCTexture() {
        return mcResourceLocation != null;
    }

    /**
     * Get the MC resource location
     */
    @Nullable
    public ResourceLocation getMcResourceLocation() {
        return mcResourceLocation;
    }

    /**
     * Don't dispose MC textures - they are managed by MC
     */
    @Override
    public void dispose() {
        // MC textures should not be disposed by our resource manager
        // They are managed by Minecraft's texture system
        if (!isMCTexture()) {
            super.dispose();
        }
    }

    /**
     * Force refresh of MC texture handle (useful for resource pack changes)
     */
    public void refreshMCHandle() {
        mcHandleResolved = false;
        mcTextureHandle = -1;
    }
}