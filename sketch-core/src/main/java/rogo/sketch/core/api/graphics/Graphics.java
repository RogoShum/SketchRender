package rogo.sketch.core.api.graphics;

import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

public interface Graphics {
    KeyId getIdentifier();

    PartialRenderSetting getPartialRenderSetting();

    boolean shouldDiscard();

    boolean shouldRender();

    default <C extends RenderContext> void afterDraw(C context) {
    }

    void clearBatchDirtyFlags();

    boolean isBatchDirty();
}