package rogo.sketch.api.graphics;

import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.util.Identifier;

public interface GraphicsInstance {
    Identifier getIdentifier();

    boolean shouldTick();

    <C extends RenderContext> void tick(C context);

    boolean shouldDiscard();

    boolean shouldRender();

    <C extends RenderContext> void afterDraw(C context);
}