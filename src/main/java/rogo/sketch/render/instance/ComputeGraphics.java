package rogo.sketch.render.instance;

import rogo.sketch.api.graphics.DispatchProvider;
import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.render.shader.ComputeShader;
import rogo.sketch.util.KeyId;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class ComputeGraphics implements Graphics, DispatchProvider {
    private final KeyId id;
    private final Consumer<RenderContext> tick;
    private final BiConsumer<RenderContext, ComputeShader> dispatch;

    public ComputeGraphics(KeyId keyId, Consumer<RenderContext> tick,
                           BiConsumer<RenderContext, ComputeShader> dispatchCommand) {
        this.id = keyId;
        this.tick = tick;
        this.dispatch = dispatchCommand;
    }

    @Override
    public KeyId getIdentifier() {
        return id;
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
}