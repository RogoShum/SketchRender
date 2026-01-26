package rogo.sketch.core.instance;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.graphics.DispatchProvider;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.shader.ComputeShader;
import rogo.sketch.core.util.KeyId;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class ComputeGraphics implements Graphics, DispatchProvider, Comparable<ComputeGraphics> {
    private final KeyId id;
    @Nullable
    private final Consumer<RenderContext> tick;
    private final BiConsumer<RenderContext, ComputeShader> dispatch;
    private int priority;

    public ComputeGraphics(KeyId keyId, @Nullable Consumer<RenderContext> tick, BiConsumer<RenderContext, ComputeShader> dispatchCommand) {
        this(keyId, tick, dispatchCommand, 100);
    }

    public ComputeGraphics(KeyId keyId, @Nullable Consumer<RenderContext> tick, BiConsumer<RenderContext, ComputeShader> dispatchCommand, int priority) {
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
    public boolean tickable() {
        return false;
    }

    @Override
    public boolean shouldTick() {
        return tick != null;
    }

    @Override
    public <C extends RenderContext> void tick(C context) {
        tick.accept(context);
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