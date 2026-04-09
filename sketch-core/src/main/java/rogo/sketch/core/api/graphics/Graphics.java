package rogo.sketch.core.api.graphics;

import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

public interface Graphics {
    KeyId getIdentifier();

    boolean shouldDiscard();

    boolean shouldRender();

    default <C extends RenderContext> void afterDraw(C context) {
    }

    default SubmissionCapability submissionCapability() {
        return SubmissionCapability.DIRECT_BATCHABLE;
    }
}

