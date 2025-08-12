package rogo.sketch.render.resource;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL42;
import rogo.sketch.api.BindingResource;
import rogo.sketch.api.ResourceObject;
import rogo.sketch.util.Identifier;

public class Texture implements ResourceObject, BindingResource {
    private final int handle;
    private final Identifier identifier;
    private final int format;
    private final int filterMode;
    private final int wrapMode;
    private boolean disposed = false;

    // Current dimensions (managed by RenderTarget)
    private int currentWidth = 0;
    private int currentHeight = 0;

    public Texture(int handle, Identifier identifier, int format, int filterMode, int wrapMode) {
        this.handle = handle;
        this.identifier = identifier;
        this.format = format;
        this.filterMode = filterMode;
        this.wrapMode = wrapMode;
    }

    /**
     * Resize the texture to new dimensions
     * This should only be called by RenderTarget management
     */
    public void resize(int width, int height) {
        if (disposed) {
            throw new IllegalStateException("Texture has been disposed");
        }

        if (width == currentWidth && height == currentHeight) {
            return; // No change needed
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, handle);

        // Resize the texture
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, width, height, 0,
                format, GL11.GL_UNSIGNED_BYTE, 0);

        this.currentWidth = width;
        this.currentHeight = height;

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    /**
     * Get the OpenGL handle
     */
    @Override
    public int getHandle() {
        return handle;
    }

    /**
     * Bind this texture to the specified texture unit
     */
    @Override
    public void bind(Identifier resourceType, int textureUnit) {
        if (disposed) {
            throw new IllegalStateException("Texture has been disposed");
        }

        if (resourceType.equals(ResourceTypes.IMAGE_BUFFER)) {
            GL42.glBindImageTexture(textureUnit, handle, 0, false, 0, GL42.GL_READ_WRITE, format);
        } else {
            GL42.glActiveTexture(GL13.GL_TEXTURE0 + textureUnit);
            GL42.glBindTexture(GL11.GL_TEXTURE_2D, handle);
        }
    }

    /**
     * Unbind texture from the specified unit
     */
    public static void unbind(int textureUnit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + textureUnit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    /**
     * Unbind texture from unit 0
     */
    public static void unbind() {
        unbind(0);
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public int getFormat() {
        return format;
    }

    public int getFilterMode() {
        return filterMode;
    }

    public int getWrapMode() {
        return wrapMode;
    }

    public int getCurrentWidth() {
        return currentWidth;
    }

    public int getCurrentHeight() {
        return currentHeight;
    }

    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void dispose() {
        if (!disposed) {
            GL11.glDeleteTextures(handle);
            disposed = true;
        }
    }
}