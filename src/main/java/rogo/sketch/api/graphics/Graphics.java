package rogo.sketch.api.graphics;

import rogo.sketch.render.pipeline.PartialRenderSetting;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.util.KeyId;

public interface Graphics {
    KeyId getIdentifier();

    PartialRenderSetting getPartialRenderSetting();

    boolean shouldTick();

    default <C extends RenderContext> void tick(C context) {
    }

    boolean shouldDiscard();

    boolean shouldRender();

    default <C extends RenderContext> void afterDraw(C context) {
    }
}