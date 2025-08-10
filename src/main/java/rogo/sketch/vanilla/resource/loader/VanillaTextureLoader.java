package rogo.sketch.vanilla.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import rogo.sketch.render.resource.loader.ResourceLoader;
import rogo.sketch.vanilla.resource.VanillaTexture;

/**
 * Loader for VanillaTexture resources from JSON (MC-specific)
 */
public class VanillaTextureLoader implements ResourceLoader<VanillaTexture> {

    @Override
    public VanillaTexture loadFromJson(String jsonData, Gson gson) {
        try {
            JsonObject json = gson.fromJson(jsonData, JsonObject.class);

            String identifier = json.get("identifier").getAsString();

            // This loader specifically handles MC textures
            if (json.has("mcResourceLocation")) {
                String mcResourceStr = json.get("mcResourceLocation").getAsString();
                ResourceLocation mcResource = new ResourceLocation(mcResourceStr);
                return new VanillaTexture(identifier, mcResource);
            } else {
                System.err.println("VanillaTextureLoader requires 'mcResourceLocation' field");
                return null;
            }

        } catch (Exception e) {
            System.err.println("Failed to load vanilla texture from JSON: " + e.getMessage());
            return null;
        }
    }

    @Override
    public Class<VanillaTexture> getResourceClass() {
        return VanillaTexture.class;
    }
} 