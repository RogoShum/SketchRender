package rogo.sketch.vanilla.resource.loader;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.loader.ResourceLoadContext;
import rogo.sketch.core.resource.loader.ResourceLoader;
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

                GraphicsDriver.getCurrentAPI().bindTexture(GL11.GL_TEXTURE_2D, handle);
                int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                GraphicsDriver.getCurrentAPI().bindTexture(GL11.GL_TEXTURE_2D, 0);

                int minFilter = GL11.GL_NEAREST;
                int magFilter = GL11.GL_NEAREST;
                int wrapS = GL11.GL_REPEAT;
                int wrapT = GL11.GL_REPEAT;

                if (json.has("minFilter"))
                    minFilter = parseFilter(json.get("minFilter").getAsString());
                if (json.has("magFilter"))
                    magFilter = parseFilter(json.get("magFilter").getAsString());
                if (json.has("wrapS"))
                    wrapS = parseWrap(json.get("wrapS").getAsString());
                if (json.has("wrapT"))
                    wrapT = parseWrap(json.get("wrapT").getAsString());

                return new VanillaTexture(context.getResourceId(), mcResource, texture, width, height, minFilter, magFilter, wrapS, wrapT);
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

    private int parseFilter(String filter) {
        return switch (filter.toUpperCase()) {
            case "NEAREST" -> GL11.GL_NEAREST;
            case "LINEAR" -> GL11.GL_LINEAR;
            case "NEAREST_MIPMAP_NEAREST" -> GL11.GL_NEAREST_MIPMAP_NEAREST;
            case "LINEAR_MIPMAP_NEAREST" -> GL11.GL_LINEAR_MIPMAP_NEAREST;
            case "NEAREST_MIPMAP_LINEAR" -> GL11.GL_NEAREST_MIPMAP_LINEAR;
            case "LINEAR_MIPMAP_LINEAR" -> GL11.GL_LINEAR_MIPMAP_LINEAR;
            default -> GL11.GL_LINEAR;
        };
    }

    private int parseWrap(String wrap) {
        return switch (wrap.toUpperCase()) {
            case "REPEAT" -> GL11.GL_REPEAT;
            case "CLAMP" -> GL11.GL_CLAMP;
            case "CLAMP_TO_EDGE" -> GL13.GL_CLAMP_TO_EDGE;
            case "CLAMP_TO_BORDER" -> GL13.GL_CLAMP_TO_BORDER;
            case "MIRRORED_REPEAT" -> GL14.GL_MIRRORED_REPEAT;
            default -> GL11.GL_REPEAT;
        };
    }
}