package rogo.sketch.core.resource.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rogo.sketch.core.backend.ResourceAllocator;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.vision.RenderTarget;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.descriptor.RenderTargetResolutionMode;
import rogo.sketch.core.resource.descriptor.ResolvedRenderTargetSpec;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.List;

/**
 * Loader for RenderTarget resources from JSON with smart texture loading
 */
public class RenderTargetLoader implements ResourceLoader<RenderTarget> {

    @Override
    public KeyId getResourceType() {
        return ResourceTypes.RENDER_TARGET;
    }

    @Override
    public RenderTarget load(ResourceLoadContext context) {
        try {
            KeyId keyId = context.getResourceId();
            JsonObject json = context.getJson();
            if (json == null)
                return null;

            // Parse resolution mode
            RenderTargetResolutionMode mode = RenderTargetResolutionMode.FIXED;
            if (json.has("resolutionMode")) {
                String modeStr = json.get("resolutionMode").getAsString();
                mode = RenderTargetResolutionMode.fromString(modeStr);
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

            GraphicsResourceManager resourceManager = GraphicsResourceManager.getInstance();
            List<KeyId> colorAttachmentIds = new ArrayList<>();
            KeyId depthAttachmentId = null;
            KeyId stencilAttachmentId = null;

            // Attach color textures using smart loading
            if (json.has("colorAttachments")) {
                JsonArray colorAttachments = json.getAsJsonArray("colorAttachments");

                for (int i = 0; i < colorAttachments.size(); i++) {
                    JsonElement element = colorAttachments.get(i);

                    // Use TextureHelper for smart texture loading
                    KeyId textureId = CoreTextureHelper.loadTextureFromElement(element, resourceManager);
                    if (textureId != null) {
                        colorAttachmentIds.add(textureId);
                    } else {
                        System.err.println("Failed to load color attachment texture at index " + i);
                    }
                }
            }

            // Attach depth texture using smart loading
            if (json.has("depthAttachment")) {
                JsonElement depthElement = json.get("depthAttachment");
                depthAttachmentId = CoreTextureHelper.loadTextureFromElement(depthElement, resourceManager);
                if (depthAttachmentId != null) {
                    // parsed into spec below
                } else {
                    System.err.println("Failed to load depth attachment texture");
                }
            }

            // Attach stencil texture using smart loading
            if (json.has("stencilAttachment")) {
                JsonElement stencilElement = json.get("stencilAttachment");
                stencilAttachmentId = CoreTextureHelper.loadTextureFromElement(stencilElement, resourceManager);
                if (stencilAttachmentId != null) {
                    // parsed into spec below
                } else {
                    System.err.println("Failed to load stencil attachment texture");
                }
            }

            ResolvedRenderTargetSpec descriptor = new ResolvedRenderTargetSpec(
                    keyId,
                    mode,
                    baseWidth,
                    baseHeight,
                    scaleX,
                    scaleY,
                    colorAttachmentIds,
                    depthAttachmentId,
                    stencilAttachmentId);
            ResourceAllocator installer = GraphicsDriver.resourceAllocator();
            return installer.createRenderTarget(keyId, descriptor);

        } catch (Exception e) {
            System.err.println("Failed to load render target from JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}

