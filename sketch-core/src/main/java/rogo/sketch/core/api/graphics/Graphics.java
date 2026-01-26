package rogo.sketch.core.api.graphics;

import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

public interface Graphics {
    KeyId getIdentifier();

    PartialRenderSetting getPartialRenderSetting();

    boolean shouldTick();

    boolean tickable();

    default <C extends RenderContext> void tick(C context) {
    }

    boolean shouldDiscard();

    boolean shouldRender();

    default <C extends RenderContext> void afterDraw(C context) {
    }
}