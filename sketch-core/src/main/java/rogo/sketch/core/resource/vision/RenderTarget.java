package rogo.sketch.core.resource.vision;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.backend.GpuHandle;
import rogo.sketch.core.resource.descriptor.ResolvedRenderTargetSpec;
import rogo.sketch.core.util.KeyId;

public abstract class RenderTarget implements ResourceObject {
    protected final GpuHandle handle;
    protected final KeyId keyId;
    protected final ResolvedRenderTargetSpec descriptor;
    protected int currentWidth, currentHeight;
    protected boolean disposed = false;

    /**
     * @deprecated Use {@link #RenderTarget(GpuHandle, KeyId, ResolvedRenderTargetSpec)} so resource handles stay backend-neutral.
     */
    @Deprecated
    public RenderTarget(int handle, KeyId keyId, ResolvedRenderTargetSpec descriptor) {
        this(GpuHandle.ofGl(handle), keyId, descriptor);
    }

    public RenderTarget(GpuHandle handle, KeyId keyId, ResolvedRenderTargetSpec descriptor) {
        this.handle = handle;
        this.keyId = keyId;
        this.descriptor = descriptor;
    }

    public GpuHandle gpuHandle() {
        return handle;
    }

    public void resize(int newWidth, int newHeight) {
        if (newWidth == currentWidth && newHeight == currentHeight) {
            return; // No change needed
        }

        this.currentWidth = newWidth;
        this.currentHeight = newHeight;
    }

    public KeyId getIdentifier() {
        return keyId;
    }

    public ResolvedRenderTargetSpec descriptor() {
        return descriptor;
    }

    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void dispose() {
        this.disposed = true;
    }

    protected final void markDisposed() {
        this.disposed = true;
    }
}

