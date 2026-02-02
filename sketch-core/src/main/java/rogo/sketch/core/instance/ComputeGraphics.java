package rogo.sketch.core.instance;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.graphics.AsyncTickable;
import rogo.sketch.core.api.graphics.DispatchableGraphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.shader.ComputeShader;
import rogo.sketch.core.util.KeyId;

import java.util.function.BiConsumer;

public abstract class ComputeGraphics implements DispatchableGraphics, AsyncTickable, Comparable<ComputeGraphics> {
    private final KeyId id;
    @Nullable
    private final Runnable tick;
    private final BiConsumer<RenderContext, ComputeShader> dispatch;
    private int priority;
    protected boolean batchDirty = false;

    public ComputeGraphics(KeyId keyId, @Nullable Runnable tick,
                           BiConsumer<RenderContext, ComputeShader> dispatchCommand) {
        this(keyId, tick, dispatchCommand, 100);
    }

    public ComputeGraphics(KeyId keyId, @Nullable Runnable tick,
                           BiConsumer<RenderContext, ComputeShader> dispatchCommand, int priority) {
        this.id = keyId;
        this.tick = tick;
        this.dispatch = dispatchCommand;
        this.priority = priority;
    }

    public ComputeGraphics setPriority(int priority) {
        this.priority = priority;
        return this;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public KeyId getIdentifier() {
        return id;
    }

    @Override
    public void asyncTick() {
        if (tick != null) {
            tick.run();
        }
    }

    @Override
    public void swapData() {
    }

    @Override
    public void clearBatchDirtyFlags() {
        batchDirty = false;
    }

    @Override
    public boolean isBatchDirty() {
        return batchDirty;
    }

    @Override
    public BiConsumer<RenderContext, ComputeShader> getDispatchCommand() {
        return dispatch;
    }

    @Override
    public int compareTo(ComputeGraphics o) {
        return Integer.compare(this.priority, o.priority);
    }
}