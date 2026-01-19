package rogo.sketch.render.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL30;
import rogo.sketch.render.resource.GraphicsResourceManager;
import rogo.sketch.render.resource.RenderTarget;
import rogo.sketch.util.KeyId;

import java.io.BufferedReader;
import java.util.Optional;
import java.util.function.Function;

/**
 * Loader for RenderTarget resources from JSON with smart texture loading
 */
public class RenderTargetLoader implements ResourceLoader<RenderTarget> {

    @Override
    public RenderTarget load(KeyId keyId, ResourceData data, Gson gson, Function<KeyId, Optional<BufferedReader>> resourceProvider) {
        try {
            String jsonData = data.getString();
            if (jsonData == null) return null;

            JsonObject json = gson.fromJson(jsonData, JsonObject.class);

            // Parse resolution mode
            RenderTarget.ResolutionMode mode = RenderTarget.ResolutionMode.FIXED;
            if (json.has("resolutionMode")) {
                String modeStr = json.get("resolutionMode").getAsString();
                mode = parseResolutionMode(modeStr);
            }

            // Parse base dimensions
            int baseWidth = json.has("width") ? json.get("width").getAsInt() : 1920;
            int baseHeight = json.has("height") ? json.get("height").getAsInt() : 1080;

            // Parse scale factors
            float scaleX = 1.0f;
            float scaleY = 1.0f;
            if (json.has("scaleX")) {
                scaleX = json.get("scaleX").getAsFloat();
            }
            if (json.has("scaleY")) {
                scaleY = json.get("scaleY").getAsFloat();
            }
            if (json.has("scale")) {
                // Uniform scale
                float scale = json.get("scale").getAsFloat();
                scaleX = scaleY = scale;
            }

            // Parse clear color (optional)
            int clearColor = 0x00000000; // Default: transparent black
            if (json.has("clearColor")) {
                String colorStr = json.get("clearColor").getAsString();
                clearColor = parseColor(colorStr);
            }

            // Create framebuffer
            int handle = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, handle);

            RenderTarget renderTarget = new RenderTarget(handle, keyId, mode,
                    baseWidth, baseHeight, scaleX, scaleY, clearColor);

            GraphicsResourceManager resourceManager = GraphicsResourceManager.getInstance();

            // Attach color textures using smart loading
            if (json.has("colorAttachments")) {
                JsonArray colorAttachments = json.getAsJsonArray("colorAttachments");

                for (int i = 0; i < colorAttachments.size(); i++) {
                    JsonElement element = colorAttachments.get(i);

                    // Use TextureHelper for smart texture loading
                    KeyId textureId = TextureHelper.loadTextureFromElement(element, resourceManager);
                    if (textureId != null) {
                        renderTarget.setColorAttachment(i, textureId);
                    } else {
                        System.err.println("Failed to load color attachment texture at index " + i);
                    }
                }
            }

            // Attach depth texture using smart loading
            if (json.has("depthAttachment")) {
                JsonElement depthElement = json.get("depthAttachment");
                KeyId depthTextureId = TextureHelper.loadTextureFromElement(depthElement, resourceManager);
                if (depthTextureId != null) {
                    renderTarget.setDepthAttachment(depthTextureId);
                } else {
                    System.err.println("Failed to load depth attachment texture");
                }
            }

            // Attach stencil texture using smart loading
            if (json.has("stencilAttachment")) {
                JsonElement stencilElement = json.get("stencilAttachment");
                KeyId stencilTextureId = TextureHelper.loadTextureFromElement(stencilElement, resourceManager);
                if (stencilTextureId != null) {
                    renderTarget.setStencilAttachment(stencilTextureId);
                } else {
                    System.err.println("Failed to load stencil attachment texture");
                }
            }

            if (json.has("keepSizeAttachments")) {
                JsonArray keepSizeAttachments = json.getAsJsonArray("keepSizeAttachments");

                for (int i = 0; i < keepSizeAttachments.size(); i++) {
                    JsonElement element = keepSizeAttachments.get(i);

                    KeyId textureId = TextureHelper.loadTextureFromElement(element, resourceManager);
                    if (textureId != null) {
                        renderTarget.keepTextureSize(textureId);
                    }
                }
            }

            // Parse clear settings
            boolean shouldClearColor = true;
            boolean clearDepth = true;
            boolean clearStencil = false;

            if (json.has("clearSettings")) {
                JsonObject clearSettings = json.getAsJsonObject("clearSettings");
                shouldClearColor = !clearSettings.has("color") || clearSettings.get("color").getAsBoolean();
                clearDepth = !clearSettings.has("depth") || clearSettings.get("depth").getAsBoolean();
                clearStencil = clearSettings.has("stencil") && clearSettings.get("stencil").getAsBoolean();
            }

            renderTarget.setClearSettings(shouldClearColor, clearDepth, clearStencil);

            // Check framebuffer completeness
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                System.err.println("Framebuffer not complete: " + status);
            }

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

            return renderTarget;

        } catch (Exception e) {
            System.err.println("Failed to load render target from JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private RenderTarget.ResolutionMode parseResolutionMode(String mode) {
        return switch (mode.toUpperCase()) {
            case "FIXED" -> RenderTarget.ResolutionMode.FIXED;
            case "SCREEN_SIZE", "SCREEN" -> RenderTarget.ResolutionMode.SCREEN_SIZE;
            case "SCREEN_RELATIVE", "RELATIVE" -> RenderTarget.ResolutionMode.SCREEN_RELATIVE;
            default -> RenderTarget.ResolutionMode.FIXED;
        };
    }

    private int parseColor(String colorStr) {
        try {
            if (colorStr.startsWith("#")) {
                // Hex color: #RRGGBB or #AARRGGBB
                String hex = colorStr.substring(1);
                if (hex.length() == 6) {
                    // RGB -> ARGB (add full alpha)
                    return (int) (0xFF000000L | Long.parseLong(hex, 16));
                } else if (hex.length() == 8) {
                    // ARGB
                    return (int) Long.parseLong(hex, 16);
                }
            } else if (colorStr.startsWith("0x")) {
                // Hex color: 0xRRGGBB or 0xAARRGGBB
                String hex = colorStr.substring(2);
                return (int) Long.parseLong(hex, 16);
            } else {
                // Try to parse as decimal
                return Integer.parseInt(colorStr);
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid color format: " + colorStr);
        }
        return 0x00000000; // Default to transparent black
    }
} 