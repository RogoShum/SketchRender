package rogo.sketch.core.resource;

import rogo.sketch.core.api.GpuObject;
import rogo.sketch.core.driver.GraphicsDriver;
import rogo.sketch.core.util.KeyId;

public abstract class RenderTarget implements GpuObject {
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
        GraphicsDriver.getCurrentAPI().bindFrameBuffer(getHandle());
    }

    public static void unbind() {
        GraphicsDriver.getCurrentAPI().bindFrameBuffer(0);
    }

    @Override
    public void dispose() {
        this.disposed = true;
    }
}