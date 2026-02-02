package rogo.sketch.core.instance;

import rogo.sketch.core.api.graphics.MeshProvider;
import rogo.sketch.core.api.graphics.MeshBasedGraphics;
import rogo.sketch.core.util.KeyId;

public abstract class MeshGraphics implements MeshProvider, MeshBasedGraphics {
    private final KeyId id;
    protected boolean batchDirty = false;

    public MeshGraphics(KeyId keyId) {
        this.id = keyId;
    }

    @Override
    public KeyId getIdentifier() {
        return id;
    }

    @Override
    public void clearBatchDirtyFlags() {
        batchDirty = false;
    }

    @Override
    public boolean isBatchDirty() {
        return batchDirty;
    }
}