package rogo.sketch.core.instance;

import rogo.sketch.core.api.graphics.MeshProvider;
import rogo.sketch.core.api.graphics.MeshBasedGraphics;
import rogo.sketch.core.pipeline.flow.dirty.DirtyReason;
import rogo.sketch.core.util.KeyId;

public abstract class MeshGraphics implements MeshProvider, MeshBasedGraphics {
    private final KeyId id;
    protected DirtyReason batchDirty = DirtyReason.NOT;

    public MeshGraphics(KeyId keyId) {
        this.id = keyId;
    }

    @Override
    public KeyId getIdentifier() {
        return id;
    }

    @Override
    public void resetBatchDirtyFlags() {
        batchDirty = DirtyReason.NOT;
    }

    @Override
    public DirtyReason getBatchDirtyFlags() {
        return batchDirty;
    }
}