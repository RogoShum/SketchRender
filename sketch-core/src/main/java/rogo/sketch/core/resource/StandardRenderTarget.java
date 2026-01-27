package rogo.sketch.core.resource;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import rogo.sketch.core.api.Resizable;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.List;

public class StandardRenderTarget extends RenderTarget implements Resizable {
    private final List<KeyId> colorAttachmentIds = new ArrayList<>();
    private KeyId depthAttachmentId;
    private KeyId stencilAttachmentId;

    // Resolution management
    private final ResolutionMode resolutionMode;
    private final int baseWidth, baseHeight;
    private final float scaleX, scaleY;

    public StandardRenderTarget(int handle, KeyId keyId, ResolutionMode mode, int baseWidth, int baseHeight, float scaleX, float scaleY, @Nullable KeyId linkedTexture) {
        super(handle, keyId);
        this.resolutionMode = mode;
        this.scaleX = scaleX;
        this.scaleY = scaleY;

        Vector2i dimension = updateDimensions(baseWidth, baseHeight, linkedTexture);
        this.baseWidth = dimension.x;
        this.baseHeight = dimension.y;
    }

    /**
     * Convenience constructor for fixed resolution
     */
    public StandardRenderTarget(int handle, KeyId keyId, int width, int height) {
        this(handle, keyId, ResolutionMode.FIXED, width, height, 1.0f, 1.0f, null);
    }

    public Vector2i updateDimensions(int baseWidth, int baseHeight, @Nullable KeyId linkedTexture) {
        Vector2i dimensions = new Vector2i(baseWidth, baseHeight);

        if (linkedTexture != null) {
            GraphicsResourceManager.getInstance().getResource(ResourceTypes.TEXTURE, linkedTexture).ifPresent(tex -> {
                int w = ((Texture) tex).getCurrentWidth();
                int h = ((Texture) tex).getCurrentHeight();
                if (w > 0 && h > 0) {
                    dimensions.x = w;
                    dimensions.y = h;
                    resize(w, h);
                }
            });
        }

        if (dimensions.x != currentWidth || dimensions.y != currentHeight) {
            resize(dimensions.x, dimensions.y);
        }

        return dimensions;
    }

    /**
     * Set color attachment by index
     */
    public void setColorAttachment(int index, KeyId textureId) {
        while (colorAttachmentIds.size() <= index) {
            colorAttachmentIds.add(null);
        }

        colorAttachmentIds.set(index, textureId);

        // Attach new texture reference
        if (textureId != null) {
            attachTextureToFramebuffer(textureId, GL30.GL_COLOR_ATTACHMENT0 + index);
        }
    }

    /**
     * Set depth attachment
     */
    public void setDepthAttachment(KeyId textureId) {
        this.depthAttachmentId = textureId;

        if (textureId != null) {
            attachTextureToFramebuffer(textureId, GL30.GL_DEPTH_ATTACHMENT);
        }
    }

    /**
     * Set stencil attachment
     */
    public void setStencilAttachment(KeyId textureId) {
        this.stencilAttachmentId = textureId;

        if (textureId != null) {
            attachTextureToFramebuffer(textureId, GL30.GL_STENCIL_ATTACHMENT);
        }
    }

    /**
     * Attach a texture to the framebuffer
     */
    private void attachTextureToFramebuffer(KeyId textureId, int attachment) {
        GraphicsResourceManager.getInstance().getResource(ResourceTypes.TEXTURE, textureId)
                .ifPresent(texture -> {
                    int previousFB = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
                    GraphicsDriver.getCurrentAPI().bindFrameBuffer(getHandle());
                    GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, attachment,
                            GL11.GL_TEXTURE_2D, texture.getHandle(), 0);
                    GraphicsDriver.getCurrentAPI().bindFrameBuffer(previousFB);
                });
    }

    @Override
    public void dispose() {
        this.disposed = true;
    }

    public int getCurrentWidth() {
        return currentWidth;
    }

    public int getCurrentHeight() {
        return currentHeight;
    }

    public ResolutionMode getResolutionMode() {
        return resolutionMode;
    }

    public int getBaseWidth() {
        return baseWidth;
    }

    public int getBaseHeight() {
        return baseHeight;
    }

    public float getScaleX() {
        return scaleX;
    }

    public float getScaleY() {
        return scaleY;
    }

    public List<KeyId> getColorAttachmentIds() {
        return new ArrayList<>(colorAttachmentIds);
    }

    public KeyId getDepthAttachmentId() {
        return depthAttachmentId;
    }

    public KeyId getStencilAttachmentId() {
        return stencilAttachmentId;
    }

    /**
     * Resolution mode enumeration
     */
    public enum ResolutionMode {
        FIXED, // Fixed resolution
        SCREEN_SIZE, // Follow screen size exactly
        SCREEN_RELATIVE // Scale relative to screen size
    }
}