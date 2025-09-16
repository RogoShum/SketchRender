package rogo.sketch.render.instance;

import rogo.sketch.render.pipeline.DrawCommand;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.util.Identifier;

public class ScreenSpaceGraphics extends SharedVertexGraphics {

    public ScreenSpaceGraphics(Identifier identifier) {
        super(identifier);
    }

    @Override
    public boolean needsVertexUpdate() {
        return false;
    }

    @Override
    public void fillVertexData(VertexFiller filler) {
        filler.position(-1.0f, 1.0f, 0.0f).uv(0, 0);
        filler.position(-1.0f, -3.0f, 0.0f).uv(0, 2);
        filler.position(3.0f, 1.0f, 0.0f).uv(2, 0);
    }

    @Override
    public DrawCommand getDrawCommand() {
        return null;
    }

    @Override
    public boolean shouldTick() {
        return false;
    }

    @Override
    public <C extends RenderContext> void tick(C context) {

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
    public <C extends RenderContext> void afterDraw(C context) {

    }
}