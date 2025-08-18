package rogo.sketch.render.resource;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import rogo.sketch.api.ResourceObject;
import rogo.sketch.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RenderTarget implements ResourceObject {
    private final int handle;
    private final Identifier identifier;
    private final List<Identifier> colorAttachmentIds = new ArrayList<>();
    private Identifier depthAttachmentId;
    private Identifier stencilAttachmentId;
    private int clearColor; // ARGB format
    private boolean disposed = false;

    // Resolution management
    private final ResolutionMode resolutionMode;
    private final int baseWidth, baseHeight;
    private final float scaleX, scaleY;
    private int currentWidth, currentHeight;

    // Texture resize tracking
    private static final Map<Identifier, ResizeInfo> globalTextureResizeTracker = new HashMap<>();

    // Clear settings
    private boolean shouldClearColor = true;
    private boolean shouldClearDepth = true;
    private boolean shouldClearStencil = false;
    private int clearBuffers = GL30.GL_COLOR_BUFFER_BIT | GL30.GL_DEPTH_BUFFER_BIT;

    public RenderTarget(int handle, Identifier identifier, ResolutionMode mode, int baseWidth, int baseHeight,
                        float scaleX, float scaleY, int clearColor) {
        this.handle = handle;
        this.identifier = identifier;
        this.resolutionMode = mode;
        this.baseWidth = baseWidth;
        this.baseHeight = baseHeight;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.clearColor = clearColor;

        // Calculate initial dimensions
        updateDimensions();
    }

    /**
     * Convenience constructor for fixed resolution
     */
    public RenderTarget(int handle, Identifier identifier, int width, int height, int clearColor) {
        this(handle, identifier, ResolutionMode.FIXED, width, height, 1.0f, 1.0f, clearColor);
    }

    /**
     * Update dimensions based on resolution mode and current screen size
     */
    public void updateDimensions() {
        int newWidth, newHeight;

        switch (resolutionMode) {
            case FIXED -> {
                newWidth = baseWidth;
                newHeight = baseHeight;
            }
            case SCREEN_SIZE -> {
                // TODO: Get actual screen size from MC or window
                newWidth = (int) (1920 * scaleX); // Placeholder
                newHeight = (int) (1080 * scaleY);
            }
            case SCREEN_RELATIVE -> {
                // TODO: Get actual screen size from MC or window
                newWidth = (int) (1920 * scaleX);
                newHeight = (int) (1080 * scaleY);
            }
            default -> {
                newWidth = baseWidth;
                newHeight = baseHeight;
            }
        }

        if (newWidth != currentWidth || newHeight != currentHeight) {
            resize(newWidth, newHeight);
        }
    }

    /**
     * Resize this render target and all attached textures
     */
    public void resize(int newWidth, int newHeight) {
        if (newWidth == currentWidth && newHeight == currentHeight) {
            return; // No change needed
        }

        this.currentWidth = newWidth;
        this.currentHeight = newHeight;

        // Resize all attached textures
        GraphicsResourceManager resourceManager = GraphicsResourceManager.getInstance();

        // Resize color attachments
        for (Identifier textureId : colorAttachmentIds) {
            if (textureId != null) {
                resizeTextureIfNeeded(resourceManager, textureId, newWidth, newHeight);
            }
        }

        // Resize depth attachment
        if (depthAttachmentId != null) {
            resizeTextureIfNeeded(resourceManager, depthAttachmentId, newWidth, newHeight);
        }

        // Resize stencil attachment
        if (stencilAttachmentId != null) {
            resizeTextureIfNeeded(resourceManager, stencilAttachmentId, newWidth, newHeight);
        }
    }

    /**
     * Intelligently resize texture, avoiding conflicts and duplicates
     */
    private void resizeTextureIfNeeded(GraphicsResourceManager resourceManager, Identifier textureId,
                                       int targetWidth, int targetHeight) {

        ResizeInfo resizeInfo = globalTextureResizeTracker.get(textureId);

        if (resizeInfo == null) {
            // First time this texture is being resized
            resizeInfo = new ResizeInfo(targetWidth, targetHeight, 1);
            globalTextureResizeTracker.put(textureId, resizeInfo);

            // Actually resize the texture
            resourceManager.getResource(ResourceTypes.TEXTURE, textureId)
                    .ifPresent(texture -> ((Texture) texture).resize(targetWidth, targetHeight));

        } else if (resizeInfo.width == targetWidth && resizeInfo.height == targetHeight) {
            // Texture already has the target size, just increment reference count
            resizeInfo.referenceCount++;

        } else {
            // Conflict: Different render targets want different sizes for the same texture
            System.err.println("WARNING: Texture " + textureId + " is used by multiple render targets with different sizes!");
            System.err.println("  Current size: " + resizeInfo.width + "x" + resizeInfo.height);
            System.err.println("  Requested size: " + targetWidth + "x" + targetHeight);
            System.err.println("  This may cause rendering issues!");

            // For now, keep the existing size and increment reference count
            resizeInfo.referenceCount++;
        }
    }

    /**
     * Set color attachment by index
     */
    public void setColorAttachment(int index, Identifier textureId) {
        while (colorAttachmentIds.size() <= index) {
            colorAttachmentIds.add(null);
        }

        // Remove old texture reference
        Identifier oldTextureId = colorAttachmentIds.get(index);
        if (oldTextureId != null) {
            decrementTextureReference(oldTextureId);
        }

        colorAttachmentIds.set(index, textureId);

        // Add new texture reference and resize if needed
        if (textureId != null) {
            resizeTextureIfNeeded(GraphicsResourceManager.getInstance(), textureId, currentWidth, currentHeight);
            attachTextureToFramebuffer(textureId, GL30.GL_COLOR_ATTACHMENT0 + index);
        }
    }

    /**
     * Set depth attachment
     */
    public void setDepthAttachment(Identifier textureId) {
        if (depthAttachmentId != null) {
            decrementTextureReference(depthAttachmentId);
        }

        this.depthAttachmentId = textureId;

        if (textureId != null) {
            resizeTextureIfNeeded(GraphicsResourceManager.getInstance(), textureId, currentWidth, currentHeight);
            attachTextureToFramebuffer(textureId, GL30.GL_DEPTH_ATTACHMENT);
        }
    }

    /**
     * Set stencil attachment
     */
    public void setStencilAttachment(Identifier textureId) {
        if (stencilAttachmentId != null) {
            decrementTextureReference(stencilAttachmentId);
        }

        this.stencilAttachmentId = textureId;

        if (textureId != null) {
            resizeTextureIfNeeded(GraphicsResourceManager.getInstance(), textureId, currentWidth, currentHeight);
            attachTextureToFramebuffer(textureId, GL30.GL_STENCIL_ATTACHMENT);
        }
    }

    /**
     * Attach a texture to the framebuffer
     */
    private void attachTextureToFramebuffer(Identifier textureId, int attachment) {
        GraphicsResourceManager.getInstance().getResource(ResourceTypes.TEXTURE, textureId)
                .ifPresent(texture -> {
                    int previousFB = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, handle);
                    GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, attachment,
                            GL11.GL_TEXTURE_2D, texture.getHandle(), 0);
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFB);
                });
    }

    /**
     * Decrement texture reference count and cleanup if needed
     */
    private void decrementTextureReference(Identifier textureId) {
        ResizeInfo resizeInfo = globalTextureResizeTracker.get(textureId);
        if (resizeInfo != null) {
            resizeInfo.referenceCount--;
            if (resizeInfo.referenceCount <= 0) {
                globalTextureResizeTracker.remove(textureId);
            }
        }
    }

    /**
     * Bind this render target for rendering
     */
    public void bind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, handle);
        GL11.glViewport(0, 0, currentWidth, currentHeight);
    }

    /**
     * Unbind render target (bind default framebuffer)
     */
    public static void unbind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
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

    // Getters and setters
    @Override
    public int getHandle() {
        return handle;
    }

    public Identifier getIdentifier() {
        return identifier;
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
        if (clearColor) this.clearBuffers |= GL30.GL_COLOR_BUFFER_BIT;
        if (clearDepth) this.clearBuffers |= GL30.GL_DEPTH_BUFFER_BIT;
        if (clearStencil) this.clearBuffers |= GL30.GL_STENCIL_BUFFER_BIT;
    }

    public List<Identifier> getColorAttachmentIds() {
        return new ArrayList<>(colorAttachmentIds);
    }

    public Identifier getDepthAttachmentId() {
        return depthAttachmentId;
    }

    public Identifier getStencilAttachmentId() {
        return stencilAttachmentId;
    }

    @Override
    public void dispose() {
        // Decrement all texture references
        for (Identifier textureId : colorAttachmentIds) {
            if (textureId != null) {
                decrementTextureReference(textureId);
            }
        }
        if (depthAttachmentId != null) {
            decrementTextureReference(depthAttachmentId);
        }
        if (stencilAttachmentId != null) {
            decrementTextureReference(stencilAttachmentId);
        }

        // Delete framebuffer
        if (handle > 0) {
            GL30.glDeleteFramebuffers(handle);
        }

        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Resolution mode enumeration
     */
    public enum ResolutionMode {
        FIXED,          // Fixed resolution
        SCREEN_SIZE,    // Follow screen size exactly
        SCREEN_RELATIVE // Scale relative to screen size
    }

    /**
     * Internal class for tracking texture resize information
     */
    private static class ResizeInfo {
        int width, height;
        int referenceCount;

        ResizeInfo(int width, int height, int referenceCount) {
            this.width = width;
            this.height = height;
            this.referenceCount = referenceCount;
        }
    }
}