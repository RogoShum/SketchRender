package rogo.sketch.core.resource.vision;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.util.KeyId;

public abstract class Texture implements ResourceObject {
    protected final int handle;
    protected final KeyId keyId;
    protected final ResolvedImageResource descriptor;

    protected int width;
    protected int height;
    protected boolean disposed = false;

    public Texture(int handle, KeyId keyId, ResolvedImageResource descriptor) {
        this.handle = handle;
        this.keyId = keyId;
        this.descriptor = descriptor;
        this.width = descriptor.width();
        this.height = descriptor.height();
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

    public ResolvedImageResource descriptor() {
        return descriptor;
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    protected final void markDisposed() {
        disposed = true;
    }
}

