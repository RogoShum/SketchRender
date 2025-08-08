package rogo.sketch.render.instance;

import rogo.sketch.render.GraphicsInstance;
import rogo.sketch.render.RenderContext;
import rogo.sketch.util.Identifier;

public class ComputeShaderGraphics extends GraphicsInstance<RenderContext> {

    public ComputeShaderGraphics(Identifier identifier) {
        super(identifier);
    }

    @Override
    public void tick() {

    }

    @Override
    public void fillBuffers() {

    }

    @Override
    public boolean shouldDiscard() {
        return false;
    }

    @Override
    public boolean shouldRender() {
        return false;
    }

    @Override
    public void render(RenderContext context) {

    }
}