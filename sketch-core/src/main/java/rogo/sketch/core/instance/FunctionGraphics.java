package rogo.sketch.core.instance;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

public abstract class FunctionGraphics implements Graphics, ResourceObject, Comparable<FunctionGraphics> {
    private final KeyId id;
    private boolean disposed = false;
    private int priority = 100;

    public FunctionGraphics(KeyId keyId) {
        this.id = keyId;
    }

    public FunctionGraphics setPriority(int priority) {
        this.priority = priority;
        return this;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * Execute the function logic (e.g. OpenGL commands).
     *
     * @param context Render context
     */
    public abstract void execute(RenderContext context);

    @Override
    public int compareTo(FunctionGraphics o) {
        return Integer.compare(this.priority, o.priority);
    }

    @Override
    public int getHandle() {
        return 0;
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
    public PartialRenderSetting getPartialRenderSetting() {
        return null;
    }

    @Override
    public boolean shouldTick() {
        return false;
    }

    @Override
    public boolean tickable() {
        return false;
    }

    @Override
    public boolean shouldDiscard() {
        return disposed;
    }

    @Override
    public boolean shouldRender() {
        return true;
    }
}