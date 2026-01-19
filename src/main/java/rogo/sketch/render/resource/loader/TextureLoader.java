package rogo.sketch.render.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import rogo.sketch.render.resource.Texture;
import rogo.sketch.util.KeyId;

import java.io.BufferedReader;
import java.util.Optional;
import java.util.function.Function;

/**
 * Loader for basic Texture resources from JSON (non-MC)
 */
public class TextureLoader implements ResourceLoader<Texture> {

    @Override
    public Texture load(KeyId keyId, ResourceData data, Gson gson, Function<KeyId, Optional<BufferedReader>> resourceProvider) {
        try {
            String jsonData = data.getString();
            if (jsonData == null) return null;

            JsonObject json = gson.fromJson(jsonData, JsonObject.class);
            // This loader only handles basic textures, not MC textures
            if (json.has("mcResourceLocation")) {
                System.err.println("TextureLoader does not handle MC textures. Use VanillaTextureLoader instead.");
                return null;
            }

            // Parse format
            int format = GL11.GL_RGBA;
            if (json.has("format")) {
                String formatStr = json.get("format").getAsString();
                format = parseFormat(formatStr);
            }

            // Parse filter mode
            int filterMode = GL11.GL_LINEAR;
            if (json.has("filter")) {
                String filterStr = json.get("filter").getAsString();
                filterMode = parseFilter(filterStr);
            }

            // Parse wrap mode
            int wrapMode = GL11.GL_REPEAT;
            if (json.has("wrap")) {
                String wrapStr = json.get("wrap").getAsString();
                wrapMode = parseWrap(wrapStr);
            }

            // Generate OpenGL texture
            int handle = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, handle);

            // Set texture parameters
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filterMode);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filterMode);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrapMode);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrapMode);

            // Initialize with minimal size (will be resized by RenderTarget)
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, 1, 1, 0,
                    format, GL11.GL_UNSIGNED_BYTE, 0);

            // Generate mipmaps if needed
            if (json.has("mipmaps") && json.get("mipmaps").getAsBoolean()) {
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            }

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            return new Texture(handle, keyId, format, filterMode, wrapMode);

        } catch (Exception e) {
            System.err.println("Failed to load texture from JSON: " + e.getMessage());
            return null;
        }
    }

    private int parseFormat(String format) {
        return switch (format.toUpperCase()) {
            case "RGB" -> GL11.GL_RGB;
            case "RGBA" -> GL11.GL_RGBA;
            case "DEPTH" -> GL11.GL_DEPTH_COMPONENT;
            case "DEPTH_STENCIL" -> GL30.GL_DEPTH_STENCIL;
            case "R" -> GL30.GL_RED;
            case "RG" -> GL30.GL_RG;
            default -> GL11.GL_RGBA;
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