package rogo.sketch.core.resource;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL42;
import rogo.sketch.core.api.BindingResource;
import rogo.sketch.core.api.GpuObject;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.driver.internal.IGLTextureStrategy;
import rogo.sketch.core.util.KeyId;

public abstract class Texture implements GpuObject, BindingResource {
    protected final int handle;
    protected final KeyId keyId;
    protected final int format;
    protected final int minFilter;
    protected final int magFilter;
    protected final int wrapS;
    protected final int wrapT;

    protected int width;
    protected int height;
    protected boolean disposed = false;

    /**
     * Get the texture strategy from the current graphics API
     */
    protected static IGLTextureStrategy getTextureStrategy() {
        return GraphicsDriver.getCurrentAPI().getTextureStrategy();
    }

    public Texture(int handle, KeyId keyId, int width, int height, int format, int minFilter, int magFilter, int wrapS, int wrapT) {
        this.handle = handle;
        this.keyId = keyId;
        this.width = width;
        this.height = height;
        this.format = format;
        this.minFilter = minFilter;
        this.magFilter = magFilter;
        this.wrapS = wrapS;
        this.wrapT = wrapT;
    }

    @Override
    public int getHandle() {
        return handle;
    }

    public KeyId getIdentifier() {
        return keyId;
    }

    public int getCurrentWidth() {
        return width;
    }

    public int getCurrentHeight() {
        return height;
    }

    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Bind this texture to the specified texture unit
     */
    @Override
    public void bind(KeyId resourceType, int textureUnit) {
        if (isDisposed()) {
            throw new IllegalStateException("Texture has been disposed");
        }

        IGLTextureStrategy strategy = getTextureStrategy();
        if (resourceType.equals(ResourceTypes.IMAGE_BUFFER)) {
            strategy.bindImageTexture(textureUnit, getHandle(), 0, false, 0, GL42.GL_READ_WRITE, format);
        } else {
            strategy.bindTextureUnit(textureUnit, getHandle());
        }
    }

    @Override
    public abstract void dispose();

    /**
     * Unbind texture from the specified unit
     */
    public static void unbind(int textureUnit) {
        getTextureStrategy().bindTextureUnit(textureUnit, 0);
    }

    public static void unbind() {
        unbind(0);
    }
}