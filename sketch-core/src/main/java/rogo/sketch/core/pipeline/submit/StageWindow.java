package rogo.sketch.core.pipeline.submit;

import java.util.List;

/**
 * Fixed stage-local execution windows used by the first submit skeleton.
 */
public enum StageWindow {
    PRE_STAGE_UPLOAD,
    PRE_STAGE_DISPATCH,
    DRAW,
    POST_STAGE;

    public static List<StageWindow> executionOrder() {
        return List.of(PRE_STAGE_UPLOAD, PRE_STAGE_DISPATCH, DRAW, POST_STAGE);
    }
}
