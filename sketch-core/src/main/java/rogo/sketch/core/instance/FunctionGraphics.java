package rogo.sketch.core.instance;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.api.graphics.FunctionalGraphics;
import rogo.sketch.core.util.KeyId;

public abstract class FunctionGraphics implements FunctionalGraphics, ResourceObject, Comparable<FunctionGraphics> {
    private final KeyId id;
    private final KeyId stageId;
    private boolean disposed = false;
    private int priority = 100;

    public FunctionGraphics(KeyId keyId, KeyId stageId) {
        this.id = keyId;
        this.stageId = stageId;
    }

    public FunctionGraphics setPriority(int priority) {
        this.priority = priority;
        return this;
    }

    public int getPriority() {
        return priority;
    }

    public KeyId stageId() {
        return stageId;
    }

    @Override
    public int compareTo(FunctionGraphics o) {
        return Integer.compare(this.priority, o.priority);
    }

    @Override
    public void dispose() {
        this.disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public KeyId getIdentifier() {
        return id;
    }

    @Override
    public boolean shouldDiscard() {
        return isDisposed();
    }

    @Override
    public boolean shouldRender() {
        return !isDisposed();
    }
}

