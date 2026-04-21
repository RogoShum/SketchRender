package rogo.sketch.core.resource.loader;

import com.google.gson.JsonObject;
import rogo.sketch.core.backend.ResourceAllocator;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.util.ImageUtil;
import rogo.sketch.core.util.KeyId;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Function;

/**
 * Loader for Texture resources from JSON.
 * Uses GraphicsAPI texture strategy for DSA/Legacy abstraction.
 */
public class TextureLoader implements ResourceLoader<Texture> {

    @Override
    public KeyId getResourceType() {
        return ResourceTypes.TEXTURE;
    }

    @Override
    public Texture load(ResourceLoadContext context) {
        try {
            KeyId keyId = context.getResourceId();
            JsonObject json = context.getJson();
            if (json == null)
                return null;
            
            Function<KeyId, Optional<InputStream>> resourceProvider = context.getSubResourceProvider();

            boolean isRenderTarget = json.has("isRenderTarget") && json.get("isRenderTarget").getAsBoolean();
            String imagePath = json.has("imagePath") ? json.get("imagePath").getAsString() : null;

            if (!isRenderTarget && imagePath == null) {
                return null;
            }

            ByteBuffer imageBuffer = null;
            int width = 0;
            int height = 0;

            if (imagePath != null) {
                ImageUtil.ImageData imageData = ImageUtil.loadImage(KeyId.of(imagePath), createImageResourceProvider(resourceProvider), true);
                if (imageData != null) {
                    width = imageData.width;
                    height = imageData.height;

                    imageBuffer = imageData.buffer;
                } else {
                    System.err.println("Image not found: " + imagePath);
                }
            }
            if (width <= 0 && json.has("width")) {
                width = json.get("width").getAsInt();
            }
            if (height <= 0 && json.has("height")) {
                height = json.get("height").getAsInt();
            }

            ResolvedImageResource descriptor = TextureDescriptorParser.parse(keyId, json, width > 0 ? width : 1, height > 0 ? height : 1, imagePath);
            ResourceAllocator installer = GraphicsDriver.resourceAllocator();
            return installer.createTexture(keyId, descriptor, imagePath, imageBuffer);

        } catch (Exception e) {
            System.err.println("Failed to load texture from JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private Function<KeyId, Optional<InputStream>> createImageResourceProvider(
            Function<KeyId, Optional<InputStream>> baseResourceProvider) {
        return (imageId) -> {
            // Parse namespace:path format
            String shaderIdStr = imageId.toString();
            String namespace, path;

            if (shaderIdStr.contains(":")) {
                String[] parts = shaderIdStr.split(":", 2);
                namespace = parts[0];
                path = parts[1];
            } else {
                namespace = "minecraft"; // default namespace
                path = shaderIdStr;
            }

            // Create shader-specific path: namespace:render/shader_type/path
            // This is for main shader files (vertex, fragment, etc.)
            KeyId shaderResourceId = KeyId.of(namespace + ":render/resource/image/" + path);
            return baseResourceProvider.apply(shaderResourceId);
        };
    }

}

