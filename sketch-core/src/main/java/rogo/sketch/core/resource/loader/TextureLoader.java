package rogo.sketch.core.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.*;
import rogo.sketch.core.driver.GraphicsAPI;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.resource.StandardTexture;
import rogo.sketch.core.resource.Texture;
import rogo.sketch.core.util.ImageUtil;
import rogo.sketch.core.util.KeyId;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Function;

/**
 * Loader for Texture resources from JSON
 */
public class TextureLoader implements ResourceLoader<Texture> {

    @Override
    public Texture load(KeyId keyId, ResourceData data, Gson gson, Function<KeyId, Optional<InputStream>> resourceProvider) {
        try {
            String jsonData = data.getString();
            if (jsonData == null)
                return null;

            JsonObject json = gson.fromJson(jsonData, JsonObject.class);

            boolean isRenderTarget = json.has("isRenderTarget") && json.get("isRenderTarget").getAsBoolean();
            String imagePath = json.has("imagePath") ? json.get("imagePath").getAsString() : null;

            if (!isRenderTarget && imagePath == null) {
                // System.err.println("Texture " + keyId + " is not an RT and has no
                // imagePath!");
                return null;
            }

            // Parse Filters
            int minFilter = GL11.GL_LINEAR;
            int magFilter = GL11.GL_LINEAR;

            if (json.has("minFilter"))
                minFilter = parseFilter(json.get("minFilter").getAsString());
            else if (json.has("filter"))
                minFilter = parseFilter(json.get("filter").getAsString());

            if (json.has("magFilter"))
                magFilter = parseFilter(json.get("magFilter").getAsString());
            else if (json.has("filter"))
                magFilter = parseFilter(json.get("filter").getAsString());

            // Parse Wraps
            int wrapS = GL11.GL_REPEAT;
            int wrapT = GL11.GL_REPEAT;

            if (json.has("wrapS"))
                wrapS = parseWrap(json.get("wrapS").getAsString());
            else if (json.has("wrap"))
                wrapS = parseWrap(json.get("wrap").getAsString());

            if (json.has("wrapT"))
                wrapT = parseWrap(json.get("wrapT").getAsString());
            else if (json.has("wrap"))
                wrapT = parseWrap(json.get("wrap").getAsString());

            // Mipmaps
            boolean useMipmap = json.has("mipmaps") && json.get("mipmaps").getAsBoolean();
            int mipmapFormat = GL11.GL_LINEAR; // Default
            if (useMipmap && json.has("mipmapFormat")) {
                mipmapFormat = parseFilter(json.get("mipmapFormat").getAsString());
            }

            // Determine Format & Load Image logic
            int internalFormat = GL11.GL_RGBA;
            int dataFormat = GL11.GL_RGBA;
            int dataType = GL11.GL_UNSIGNED_BYTE;

            ByteBuffer imageBuffer = null;
            int width = 0;
            int height = 0;

            if (json.has("format")) {
                internalFormat = parseInternalFormat(json.get("format").getAsString());
                // 自动推断 uploadFormat 和 type
                dataFormat = inferBaseFormat(internalFormat);
                dataType = inferDataType(internalFormat);
            }

            if (imagePath != null) {
                ImageUtil.ImageData imageData = ImageUtil.loadImage(KeyId.of(imagePath), createImageResourceProvider(resourceProvider), true);
                if (imageData != null) {
                    width = imageData.width;
                    height = imageData.height;

                    // Infer format if not explicitly set
                    if (!json.has("format")) {
                        internalFormat = imageData.hasAlpha ? GL11.GL_RGBA : GL11.GL_RGB;
                        dataFormat = imageData.hasAlpha ? GL11.GL_RGBA : GL11.GL_RGB;
                    }

                    imageBuffer = imageData.buffer;
                } else {
                    System.err.println("Image not found: " + imagePath);
                }
            }

            // Create Texture
            GraphicsAPI api = GraphicsDriver.getCurrentAPI();
            int handle = api.genTextures();
            api.bindTexture(GL11.GL_TEXTURE_2D, handle);

            api.texParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
            api.texParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
            api.texParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrapS);
            api.texParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrapT);

            if (imageBuffer != null) {
                api.texImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, dataFormat, dataType, imageBuffer);

                if (useMipmap) {
                    api.generateMipmap(GL11.GL_TEXTURE_2D);
                }
            } else if (isRenderTarget) {
                int rtW = json.has("width") ? json.get("width").getAsInt() : 1;
                int rtH = json.has("height") ? json.get("height").getAsInt() : 1;

                api.texImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, rtW, rtH, 0, dataFormat, dataType, null);

                if (useMipmap) {
                    api.generateMipmap(GL11.GL_TEXTURE_2D);
                }
            }

            api.bindTexture(GL11.GL_TEXTURE_2D, 0);

            StandardTexture tex = new StandardTexture(handle, keyId, width, height, internalFormat, dataFormat, dataType,
                    minFilter, magFilter, wrapS, wrapT,
                    useMipmap, mipmapFormat, isRenderTarget, imagePath);
            if (imageBuffer != null && !isRenderTarget) {
                tex.updateCurrentSize(width, height);
            }

            return tex;

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

    private int parseInternalFormat(String format) {
        return switch (format.toUpperCase()) {
            // Base / Standard
            case "RGB", "RGB8" -> GL11.GL_RGB8;
            case "RGBA", "RGBA8" -> GL11.GL_RGBA8;
            case "R", "R8" -> GL30.GL_R8;
            case "RG", "RG8" -> GL30.GL_RG8;

            // SRGB
            case "SRGB", "SRGB8" -> GL21.GL_SRGB8;
            case "SRGB_ALPHA", "SRGB8_ALPHA8" -> GL21.GL_SRGB8_ALPHA8;

            // Float (HDR)
            case "RGBA16F" -> GL30.GL_RGBA16F;
            case "RGBA32F" -> GL30.GL_RGBA32F;
            case "RGB16F" -> GL30.GL_RGB16F;
            case "RGB32F" -> GL30.GL_RGB32F;
            case "R16F" -> GL30.GL_R16F;
            case "R32F" -> GL30.GL_R32F;
            case "RG16F" -> GL30.GL_RG16F;
            case "RG32F" -> GL30.GL_RG32F;

            // Depth / Stencil
            case "DEPTH", "DEPTH16" -> GL14.GL_DEPTH_COMPONENT16;
            case "DEPTH24" -> GL14.GL_DEPTH_COMPONENT24;
            case "DEPTH32F" -> GL30.GL_DEPTH_COMPONENT32F;
            case "DEPTH24_STENCIL8" -> GL30.GL_DEPTH24_STENCIL8;

            default -> GL11.GL_RGBA8;
        };
    }

    private int inferBaseFormat(int internalFormat) {
        return switch (internalFormat) {
            case GL30.GL_R8, GL30.GL_R16F, GL30.GL_R32F -> GL11.GL_RED;
            case GL30.GL_RG8, GL30.GL_RG16F, GL30.GL_RG32F -> GL30.GL_RG;

            case GL11.GL_RGB8, GL30.GL_RGB16F, GL30.GL_RGB32F,
                 0x8C41 /* GL_SRGB8 */ -> GL11.GL_RGB;

            case GL11.GL_RGBA8, GL30.GL_RGBA16F, GL30.GL_RGBA32F,
                 0x8C43 /* GL_SRGB8_ALPHA8 */, GL11.GL_RGBA -> GL11.GL_RGBA;

            case GL14.GL_DEPTH_COMPONENT16, GL14.GL_DEPTH_COMPONENT24,
                 GL30.GL_DEPTH_COMPONENT32F -> GL11.GL_DEPTH_COMPONENT;

            case GL30.GL_DEPTH24_STENCIL8 -> GL30.GL_DEPTH_STENCIL;

            default -> GL11.GL_RGBA;
        };
    }

    private int inferDataType(int internalFormat) {
        return switch (internalFormat) {
            case GL30.GL_RGBA16F, GL30.GL_RGBA32F,
                 GL30.GL_RGB16F, GL30.GL_RGB32F,
                 GL30.GL_R16F, GL30.GL_R32F,
                 GL30.GL_RG16F, GL30.GL_RG32F -> GL11.GL_FLOAT;

            case GL14.GL_DEPTH_COMPONENT16 -> GL11.GL_UNSIGNED_SHORT;
            case GL14.GL_DEPTH_COMPONENT24 -> GL11.GL_UNSIGNED_INT;
            case GL30.GL_DEPTH_COMPONENT32F -> GL11.GL_FLOAT;
            case GL30.GL_DEPTH24_STENCIL8 -> GL30.GL_UNSIGNED_INT_24_8;

            default -> GL11.GL_UNSIGNED_BYTE;
        };
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