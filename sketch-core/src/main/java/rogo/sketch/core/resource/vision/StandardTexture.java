package rogo.sketch.core.resource.vision;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.Resizable;
import rogo.sketch.core.backend.GpuHandle;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.util.KeyId;

public class StandardTexture extends Texture implements Resizable {
    @Nullable
    protected final String imagePath;

    // Current dimensions (managed by RenderTarget or Image)
    protected int currentWidth = 0;
    protected int currentHeight = 0;

    /**
     * @deprecated Use {@link #StandardTexture(GpuHandle, KeyId, ResolvedImageResource, String)} so GL names do not leak into shared resource code.
     */
    @Deprecated
    public StandardTexture(int handle, KeyId keyId, ResolvedImageResource descriptor, @Nullable String imagePath) {
        super(handle, keyId, descriptor);
        this.imagePath = imagePath;
    }

    public StandardTexture(GpuHandle handle, KeyId keyId, ResolvedImageResource descriptor, @Nullable String imagePath) {
        super(handle, keyId, descriptor);
        this.imagePath = imagePath;
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
            return;
        }

        this.currentWidth = width;
        this.currentHeight = height;
    }

    public boolean isRenderTargetAttachment() {
        return descriptor.isRenderTargetAttachment();
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
        markDisposed();
    }
}

