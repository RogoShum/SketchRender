package rogo.sketch.core.resource;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import rogo.sketch.core.api.Resizable;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.util.KeyId;

public class StandardTexture extends Texture implements Resizable {
    protected final boolean useMipmap;
    protected final int mipmapFormat;
    protected final int type;
    protected final int internalFormat;

    protected final boolean isRenderTargetAttachment;
    @Nullable
    protected final String imagePath;

    // Current dimensions (managed by RenderTarget or Image)
    protected int currentWidth = 0;
    protected int currentHeight = 0;

    public StandardTexture(int handle, KeyId keyId, int width, int height, int internalFormat, int format, int type, int minFilterMode, int magFilterMode, int wrapSMode, int wrapTMode, boolean useMipmap, int mipmapFormat, boolean isRenderTargetAttachment, @Nullable String imagePath) {
        super(handle, keyId, width, height, format, minFilterMode, magFilterMode, wrapSMode, wrapTMode);
        this.useMipmap = useMipmap;
        this.mipmapFormat = mipmapFormat;
        this.isRenderTargetAttachment = isRenderTargetAttachment;
        this.imagePath = imagePath;
        this.type = type;
        this.internalFormat = internalFormat;
    }

    /**
     * Resize the texture to new dimensions
     * This should only be called by RenderTarget management
     */
    @Override
    public void resize(int width, int height) {
        if (isDisposed()) {
            throw new IllegalStateException("Texture has been disposed");
        }

        if (width == currentWidth && height == currentHeight) {
            return; // No change needed
        }

        GraphicsDriver.getCurrentAPI().bindTexture(GL11.GL_TEXTURE_2D, handle);

        // Resize the texture
        GraphicsDriver.getCurrentAPI().texImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0,
                format, type, null);

        this.currentWidth = width;
        this.currentHeight = height;

        GraphicsDriver.getCurrentAPI().bindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    public int getFormat() {
        return format;
    }

    public int getInternalFormat() {
        return internalFormat;
    }

    public int getType() {
        return type;
    }

    public int getMinFilterMode() {
        return minFilter;
    }

    public int getMagFilterMode() {
        return magFilter;
    }

    public int getWrapSMode() {
        return wrapS;
    }

    public int getWrapTMode() {
        return wrapT;
    }

    public boolean isUseMipmap() {
        return useMipmap;
    }

    public int getMipmapFormat() {
        return mipmapFormat;
    }

    public boolean isRenderTargetAttachment() {
        return isRenderTargetAttachment;
    }

    public String getImagePath() {
        return imagePath;
    }

    @Override
    public int getCurrentWidth() {
        return currentWidth;
    }

    @Override
    public int getCurrentHeight() {
        return currentHeight;
    }

    public void updateCurrentSize(int width, int height) {
        currentWidth = width;
        currentHeight = height;
    }

    @Override
    public void dispose() {
        if (!disposed) {
            GraphicsDriver.getCurrentAPI().deleteTextures(handle);
            disposed = true;
        }
    }
}