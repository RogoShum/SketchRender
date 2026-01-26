package rogo.sketch.core.resource;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import rogo.sketch.core.api.Resizable;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.List;

public class StandardRenderTarget extends RenderTarget implements Resizable {
    private final List<KeyId> colorAttachmentIds = new ArrayList<>();
    private KeyId depthAttachmentId;
    private KeyId stencilAttachmentId;
    private int clearColor; // ARGB format

    // Resolution management
    private final ResolutionMode resolutionMode;
    private final int baseWidth, baseHeight;
    private final float scaleX, scaleY;

    // Clear settings
    private boolean shouldClearColor = true;
    private boolean shouldClearDepth = true;
    private boolean shouldClearStencil = false;
    private int clearBuffers = GL30.GL_COLOR_BUFFER_BIT | GL30.GL_DEPTH_BUFFER_BIT;

    private KeyId linkedTextureId; // Texture to link size to

    public StandardRenderTarget(int handle, KeyId keyId, ResolutionMode mode, int baseWidth, int baseHeight, float scaleX, float scaleY, int clearColor, @Nullable KeyId linkedTexture) {
        super(handle, keyId);
        this.resolutionMode = mode;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.clearColor = clearColor;

        Vector2i dimension = updateDimensions(baseWidth, baseHeight, linkedTexture);
        this.baseWidth = dimension.x;
        this.baseHeight = dimension.y;
    }

    /**
     * Convenience constructor for fixed resolution
     */
    public StandardRenderTarget(int handle, KeyId keyId, int width, int height, int clearColor) {
        this(handle, keyId, ResolutionMode.FIXED, width, height, 1.0f, 1.0f, clearColor, null);
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
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, handle);
                    GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, attachment,
                            GL11.GL_TEXTURE_2D, texture.getHandle(), 0);
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFB);
                });
    }

    @Override
    public void dispose() {
        this.disposed = true;
    }

    /**
     * Clear the render target
     */
    public void clear() {
        bind();

        if (shouldClearColor) {
            float r = ((clearColor >> 16) & 0xFF) / 255.0f;
            float g = ((clearColor >> 8) & 0xFF) / 255.0f;
            float b = (clearColor & 0xFF) / 255.0f;
            float a = ((clearColor >> 24) & 0xFF) / 255.0f;
            GL11.glClearColor(r, g, b, a);
        }

        GL11.glClear(clearBuffers);
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

    public int getClearColor() {
        return clearColor;
    }

    public void setClearColor(int clearColor) {
        this.clearColor = clearColor;
    }

    public void setClearSettings(boolean clearColor, boolean clearDepth, boolean clearStencil) {
        this.shouldClearColor = clearColor;
        this.shouldClearDepth = clearDepth;
        this.shouldClearStencil = clearStencil;

        // Update clear buffer mask
        this.clearBuffers = 0;
        if (clearColor)
            this.clearBuffers |= GL30.GL_COLOR_BUFFER_BIT;
        if (clearDepth)
            this.clearBuffers |= GL30.GL_DEPTH_BUFFER_BIT;
        if (clearStencil)
            this.clearBuffers |= GL30.GL_STENCIL_BUFFER_BIT;
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