package rogo.sketch.feature.culling.graphics;

import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.render.DrawCommand;
import rogo.sketch.render.RenderContext;
import rogo.sketch.render.data.filler.VertexFiller;
import rogo.sketch.render.instance.SharedVertexGraphics;
import rogo.sketch.util.Identifier;

public class CullingTestGraphics extends SharedVertexGraphics {

    public CullingTestGraphics(Identifier identifier) {
        super(identifier);
    }

    @Override
    public boolean needsVertexUpdate() {
        return true;
    }

    @Override
    public void fillVertexData(VertexFiller filler) {
        filler.position(-1.0f, -1.0f, 0.0f).nextVertex();
        filler.position(1.0f, -1.0f, 0.0f).nextVertex();
        filler.position(1.0f, 1.0f, 0.0f).nextVertex();
        filler.position(-1.0f, 1.0f, 0.0f).nextVertex();
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
        if (!CullingStateManager.anyCulling() || CullingStateManager.checkCulling)
            return false;

        return CullingStateManager.DEBUG > 0;
    }

    @Override
    public <C extends RenderContext> void afterDraw(C context) {

    }
}