package rogo.sketch.core.pipeline.submit;

import rogo.sketch.core.packet.ExecutionDomain;

import java.util.List;

/**
 * Fixed stage-local execution windows used by the first submit skeleton.
 */
public enum StageWindow {
    PRE_STAGE_UPLOAD(ExecutionDomain.TRANSFER),
    PRE_STAGE_DISPATCH(ExecutionDomain.COMPUTE),
    DRAW(ExecutionDomain.RASTER),
    POST_STAGE(ExecutionDomain.RASTER);

    private final ExecutionDomain executionDomain;

    StageWindow(ExecutionDomain executionDomain) {
        this.executionDomain = executionDomain;
    }

    public static List<StageWindow> executionOrder() {
        return List.of(PRE_STAGE_UPLOAD, PRE_STAGE_DISPATCH, DRAW, POST_STAGE);
    }

    public ExecutionDomain executionDomain() {
        return executionDomain;
    }
}
