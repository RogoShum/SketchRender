package rogo.sketch.render.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.ResourceTypes;
import rogo.sketch.util.Identifier;
import rogo.sketch.vanilla.resource.VanillaTexture;
import rogo.sketch.vanilla.resource.loader.VanillaTextureLoader;

import java.util.Optional;
import java.util.function.Function;

/**
 * Helper class for smart texture loading and registration
 */
public class TextureHelper {

    private static final Gson gson = new Gson();

    /**
     * Smart texture loading from JSON element
     * Supports both string identifiers and inline texture definitions
     *
     * @param element         JSON element (string identifier or texture object)
     * @param resourceManager Resource manager instance
     * @return Texture identifier that can be used to reference the texture
     */
    public static Identifier loadTextureFromElement(JsonElement element, GraphicsResourceManager resourceManager) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            // Simple string identifier - texture should already exist
            return Identifier.of(element.getAsString());

        } else if (element.isJsonObject()) {
            JsonObject textureObj = element.getAsJsonObject();

            if (!textureObj.has("identifier")) {
                // Generate a unique identifier for inline texture
                String generatedId = "inline_texture_" + System.nanoTime();
                textureObj.addProperty("identifier", generatedId);
            }

            String identifier = textureObj.get("identifier").getAsString();
            Identifier textureId = Identifier.of(identifier);

            // Check if texture already exists
            if (resourceManager.hasResource(ResourceTypes.TEXTURE, textureId)) {
                return textureId; // Already registered
            }

            // Register the inline texture
            String textureJson = gson.toJson(textureObj);

            // Determine if this is a MC texture or regular texture
            if (textureObj.has("mcResourceLocation")) {
                // Register as VanillaTexture
                VanillaTextureLoader vanillaLoader = new VanillaTextureLoader();
                VanillaTexture vanillaTexture = vanillaLoader.loadFromJson(textureId, textureJson, gson);
                if (vanillaTexture != null) {
                    resourceManager.registerDirect(ResourceTypes.TEXTURE, textureId, vanillaTexture);
                }
            } else {
                // Register as regular Texture
                resourceManager.registerJson(ResourceTypes.TEXTURE, textureId, textureJson);
            }

            return textureId;

        } else {
            throw new IllegalArgumentException("Invalid texture element: must be string identifier or texture object");
        }
    }

    /**
     * Load texture array from JSON array
     * Each element can be either a string identifier or inline texture definition
     */
    public static Identifier[] loadTextureArray(JsonElement arrayElement, GraphicsResourceManager resourceManager) {
        if (!arrayElement.isJsonArray()) {
            throw new IllegalArgumentException("Expected JSON array for texture array");
        }

        var array = arrayElement.getAsJsonArray();
        Identifier[] textureIds = new Identifier[array.size()];

        for (int i = 0; i < array.size(); i++) {
            textureIds[i] = loadTextureFromElement(array.get(i), resourceManager);
        }

        return textureIds;
    }

    /**
     * Create a simple color texture definition
     */
    public static JsonObject createColorTexture(String identifier, String format, String filter, String wrap) {
        JsonObject textureObj = new JsonObject();
        textureObj.addProperty("identifier", identifier);
        textureObj.addProperty("format", format);
        textureObj.addProperty("filter", filter);
        textureObj.addProperty("wrap", wrap);
        return textureObj;
    }

    /**
     * Create a MC texture definition
     */
    public static JsonObject createMCTexture(String identifier, String mcResourceLocation) {
        JsonObject textureObj = new JsonObject();
        textureObj.addProperty("identifier", identifier);
        textureObj.addProperty("mcResourceLocation", mcResourceLocation);
        return textureObj;
    }

    /**
     * Convenience method to create and register a basic texture
     */
    public static Identifier registerBasicTexture(GraphicsResourceManager resourceManager,
                                                  String identifier, String format, String filter, String wrap) {
        JsonObject textureObj = createColorTexture(identifier, format, filter, wrap);
        Identifier textureId = Identifier.of(identifier);

        String textureJson = gson.toJson(textureObj);
        resourceManager.registerJson(ResourceTypes.TEXTURE, textureId, textureJson);

        return textureId;
    }

    /**
     * Convenience method to create and register a MC texture
     */
    public static Identifier registerMCTexture(GraphicsResourceManager resourceManager,
                                               String identifier, String mcResourceLocation) {
        JsonObject textureObj = createMCTexture(identifier, mcResourceLocation);
        Identifier textureId = Identifier.of(identifier);

        VanillaTextureLoader vanillaLoader = new VanillaTextureLoader();
        String textureJson = gson.toJson(textureObj);
        VanillaTexture vanillaTexture = vanillaLoader.loadFromJson(textureId, textureJson, gson);

        if (vanillaTexture != null) {
            resourceManager.registerDirect(ResourceTypes.TEXTURE, textureId, vanillaTexture);
        }

        return textureId;
    }
} 