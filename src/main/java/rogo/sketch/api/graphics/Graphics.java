package rogo.sketch.api.graphics;

import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.util.Identifier;

public interface Graphics {
    Identifier getIdentifier();

    boolean shouldTick();

    default <C extends RenderContext> void tick(C context) {
    }

    boolean shouldDiscard();

    boolean shouldRender();

    default <C extends RenderContext> void afterDraw(C context) {
    }
}