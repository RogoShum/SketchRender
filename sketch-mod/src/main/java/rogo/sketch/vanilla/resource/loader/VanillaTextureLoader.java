package rogo.sketch.vanilla.resource.loader;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.loader.ResourceLoadContext;
import rogo.sketch.core.resource.loader.ResourceLoader;
import rogo.sketch.core.resource.loader.TextureDescriptorParser;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.vanilla.resource.VanillaTexture;

/**
 * Loader for VanillaTexture resources from JSON (MC-specific)
 */
public class VanillaTextureLoader implements ResourceLoader<VanillaTexture> {
    private final TextureManager textureManager = Minecraft.getInstance().getTextureManager();

    @Override
    public VanillaTexture load(ResourceLoadContext context) {
        try {
            String data = context.getString();
            if (data == null)
                return null;

            JsonObject json = context.getGson().fromJson(data, JsonObject.class);

            String mcResourceStr = null;
            if (json.has("mcResourceLocation")) {
                mcResourceStr = json.get("mcResourceLocation").getAsString();
            } else if (json.has("resourceLocation")) {
                mcResourceStr = json.get("resourceLocation").getAsString();
            }

            if (mcResourceStr != null) {
                ResourceLocation mcResource = new ResourceLocation(mcResourceStr);
                AbstractTexture texture = textureManager.getTexture(mcResource);
                int handle = texture.getId();

                GL11.glBindTexture(GL11.GL_TEXTURE_2D, handle);
                int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

                ResolvedImageResource descriptor = TextureDescriptorParser.parse(context.getResourceId(), json, width, height, mcResourceStr);
                return new VanillaTexture(context.getResourceId(), mcResource, texture, descriptor);
            }

            return null;
        } catch (Exception e) {
            System.err.println("Failed to load vanilla texture from JSON: " + e.getMessage());
            return null;
        }
    }

    @Override
    public KeyId getResourceType() {
        return ResourceTypes.TEXTURE;
    }
}

