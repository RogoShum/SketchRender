package rogo.sketch.core.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;

/**
 * Helper class for smart texture loading and registration (Core version)
 */
public class CoreTextureHelper {

    private static final Gson gson = new Gson();

    /**
     * Smart texture loading from JSON element
     * Supports both string identifiers and inline texture definitions
     *
     * @param element         JSON element (string identifier or texture object)
     * @param resourceManager Resource manager instance
     * @return Texture identifier that can be used to reference the texture
     */
    public static KeyId loadTextureFromElement(JsonElement element, GraphicsResourceManager resourceManager) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            // Simple string identifier - texture should already exist
            return KeyId.of(element.getAsString());

        } else if (element.isJsonObject()) {
            JsonObject textureObj = element.getAsJsonObject();

            if (!textureObj.has("identifier")) {
                // Generate a unique identifier for inline texture
                String generatedId = "inline_texture_" + System.nanoTime();
                textureObj.addProperty("identifier", generatedId);
            }

            String identifier = textureObj.get("identifier").getAsString();
            KeyId textureId = KeyId.of(identifier);

            // Check if texture already exists
            if (resourceManager.hasResource(ResourceTypes.TEXTURE, textureId)) {
                return textureId; // Already registered
            }

            // Register the inline texture
            String textureJson = gson.toJson(textureObj);

            // Core version does NOT support mcResourceLocation inline
            if (textureObj.has("mcResourceLocation")) {
                System.err.println("Warning: CoreTextureHelper cannot load MC texture inline: " + identifier);
                return null;
            } else {
                // Register as regular Texture
                resourceManager.registerJson(ResourceTypes.TEXTURE, textureId, textureJson);
            }

            return textureId;

        } else {
            throw new IllegalArgumentException("Invalid texture element: must be string identifier or texture object");
        }
    }
}