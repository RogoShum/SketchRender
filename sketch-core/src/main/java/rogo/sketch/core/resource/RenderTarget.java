package rogo.sketch.core.resource;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.util.KeyId;

public abstract class RenderTarget implements ResourceObject {
    protected final int handle;
    protected final KeyId keyId;
    protected int currentWidth, currentHeight;
    protected boolean disposed = false;

    public RenderTarget(int handle, KeyId keyId) {
        this.handle = handle;
        this.keyId = keyId;
    }

    public void resize(int newWidth, int newHeight) {
        if (newWidth == currentWidth && newHeight == currentHeight) {
            return; // No change needed
        }

        this.currentWidth = newWidth;
        this.currentHeight = newHeight;
    }

    @Override
    public int getHandle() {
        return handle;
    }

    public KeyId getIdentifier() {
        return keyId;
    }

    public boolean isDisposed() {
        return disposed;
    }

    public void bind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, handle);
        GL11.glViewport(0, 0, currentWidth, currentHeight);
    }

    public static void unbind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    @Override
    public void dispose() {
        this.disposed = true;
    }
}