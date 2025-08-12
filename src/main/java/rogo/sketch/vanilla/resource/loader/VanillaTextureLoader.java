package rogo.sketch.vanilla.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import rogo.sketch.render.resource.loader.ResourceLoader;
import rogo.sketch.util.Identifier;
import rogo.sketch.vanilla.resource.VanillaTexture;

import java.io.BufferedReader;
import java.util.Optional;
import java.util.function.Function;

/**
 * Loader for VanillaTexture resources from JSON (MC-specific)
 */
public class VanillaTextureLoader implements ResourceLoader<VanillaTexture> {

    @Override
    public VanillaTexture loadFromJson(Identifier identifier, String jsonData, Gson gson, Function<Identifier, Optional<BufferedReader>> resourceProvider) {
        try {
            JsonObject json = gson.fromJson(jsonData, JsonObject.class);

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
} 