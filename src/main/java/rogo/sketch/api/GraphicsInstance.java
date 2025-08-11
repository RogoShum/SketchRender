package rogo.sketch.api;

import rogo.sketch.render.RenderContext;
import rogo.sketch.util.Identifier;

public interface GraphicsInstance {
    Identifier getIdentifier();

    boolean shouldTick();

    <C extends RenderContext> void tick(C context);

    boolean shouldDiscard();

    boolean shouldRender();

    void endDraw();
}