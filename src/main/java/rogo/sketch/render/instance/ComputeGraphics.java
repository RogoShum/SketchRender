package rogo.sketch.render.instance;

import rogo.sketch.api.GraphicsInstance;
import rogo.sketch.api.ResourceReloadable;
import rogo.sketch.api.ShaderProvider;
import rogo.sketch.render.RenderContext;
import rogo.sketch.render.shader.ComputeShader;
import rogo.sketch.render.shader.Shader;
import rogo.sketch.util.Identifier;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class ComputeGraphics implements GraphicsInstance {
    private final Identifier id;
    private final Consumer<RenderContext> tick;
    private final BiConsumer<RenderContext, ComputeShader> dispatch;

    public ComputeGraphics(Identifier identifier, Consumer<RenderContext> tick, BiConsumer<RenderContext, ComputeShader> dispatchCommand) {
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
        ShaderProvider shader = context.shaderProvider();
        if (shader instanceof ComputeShader computeShader) {
            dispatch.accept(context, computeShader);
        } else if (shader instanceof ResourceReloadable<?> reloadable) {
            // Handle reloadable shaders
            Shader currentShader = (Shader) reloadable.getCurrentResource();
            if (currentShader instanceof ComputeShader computeShader) {
                dispatch.accept(context, computeShader);
            }
        }
    }
}