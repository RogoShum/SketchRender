package rogo.sketch.core.resource.loader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL30;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.RenderTarget;
import rogo.sketch.core.resource.StandardRenderTarget;
import rogo.sketch.core.util.KeyId;

import java.io.InputStream;
import java.util.Optional;
import java.util.function.Function;

/**
 * Loader for RenderTarget resources from JSON with smart texture loading
 */
public class RenderTargetLoader implements ResourceLoader<RenderTarget> {

    @Override
    public RenderTarget load(KeyId keyId, ResourceData data, Gson gson, Function<KeyId, Optional<InputStream>> resourceProvider) {
        try {
            String jsonData = data.getString();
            if (jsonData == null)
                return null;

            JsonObject json = gson.fromJson(jsonData, JsonObject.class);

            // Parse resolution mode
            StandardRenderTarget.ResolutionMode mode = StandardRenderTarget.ResolutionMode.FIXED;
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

            // Create framebuffer
            int handle = GL30.glGenFramebuffers();
            GraphicsDriver.getCurrentAPI().bindFrameBuffer(handle);

            StandardRenderTarget renderTarget = new StandardRenderTarget(handle, keyId, mode, baseWidth, baseHeight, scaleX, scaleY, null);
            GraphicsResourceManager resourceManager = GraphicsResourceManager.getInstance();

            // Attach color textures using smart loading
            if (json.has("colorAttachments")) {
                JsonArray colorAttachments = json.getAsJsonArray("colorAttachments");

                for (int i = 0; i < colorAttachments.size(); i++) {
                    JsonElement element = colorAttachments.get(i);

                    // Use TextureHelper for smart texture loading
                    KeyId textureId = CoreTextureHelper.loadTextureFromElement(element, resourceManager);
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
                KeyId depthTextureId = CoreTextureHelper.loadTextureFromElement(depthElement, resourceManager);
                if (depthTextureId != null) {
                    renderTarget.setDepthAttachment(depthTextureId);
                } else {
                    System.err.println("Failed to load depth attachment texture");
                }
            }

            // Attach stencil texture using smart loading
            if (json.has("stencilAttachment")) {
                JsonElement stencilElement = json.get("stencilAttachment");
                KeyId stencilTextureId = CoreTextureHelper.loadTextureFromElement(stencilElement, resourceManager);
                if (stencilTextureId != null) {
                    renderTarget.setStencilAttachment(stencilTextureId);
                } else {
                    System.err.println("Failed to load stencil attachment texture");
                }
            }

            // Check framebuffer completeness
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                System.err.println("Framebuffer not complete: " + status);
            }

            GraphicsDriver.getCurrentAPI().bindFrameBuffer(0);

            return renderTarget;

        } catch (Exception e) {
            System.err.println("Failed to load render target from JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private StandardRenderTarget.ResolutionMode parseResolutionMode(String mode) {
        return switch (mode.toUpperCase()) {
            case "FIXED" -> StandardRenderTarget.ResolutionMode.FIXED;
            case "SCREEN_SIZE", "SCREEN" -> StandardRenderTarget.ResolutionMode.SCREEN_SIZE;
            case "SCREEN_RELATIVE", "RELATIVE" -> StandardRenderTarget.ResolutionMode.SCREEN_RELATIVE;
            default -> StandardRenderTarget.ResolutionMode.FIXED;
        };
    }
}