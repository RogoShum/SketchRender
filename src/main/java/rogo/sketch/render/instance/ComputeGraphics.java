package rogo.sketch.render.instance;

import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.render.RenderContext;
import rogo.sketch.util.Identifier;

import java.util.function.Consumer;

public abstract class ComputeGraphics implements GraphicsInstance {
    private final Identifier id;
    private final Consumer<RenderContext> tick;
    private final Consumer<RenderContext> dispatch;

    public ComputeGraphics(Identifier identifier, Consumer<RenderContext> tick, Consumer<RenderContext> dispatchCommand) {
        this.id = identifier;
        this.tick = tick;
        this.dispatch = dispatchCommand;
    }

    @Override
    public Identifier getIdentifier() {
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
    public <C extends RenderContext> void afterDraw(C context) {
        dispatch.accept(context);
    }
}