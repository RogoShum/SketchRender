package rogo.sketch.core.pipeline.indirect;

import rogo.sketch.core.util.KeyId;

public record IndirectPlanRequest(
        KeyId stageId,
        KeyId graphicsId,
        RequestMode requestMode
) {
    public IndirectPlanRequest {
        stageId = stageId != null ? stageId : KeyId.of("sketch:unknown_stage");
        graphicsId = graphicsId != null ? graphicsId : KeyId.of("sketch:unknown_graphics");
        requestMode = requestMode != null ? requestMode : RequestMode.INDIRECT;
    }

    public enum RequestMode {
        INDIRECT,
        GPU_CULL
    }
}

