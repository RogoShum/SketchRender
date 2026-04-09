package rogo.sketch.core.pipeline.indirect;

import rogo.sketch.core.packet.DrawPlan;
import rogo.sketch.core.util.KeyId;

public record IndirectRewriteResult(
        KeyId stageId,
        KeyId graphicsId,
        IndirectPlanRequest.RequestMode requestMode,
        DrawPlan.DrawSubmission submission,
        boolean honored,
        String reason
) {
    public IndirectRewriteResult {
        stageId = stageId != null ? stageId : KeyId.of("sketch:unknown_stage");
        graphicsId = graphicsId != null ? graphicsId : KeyId.of("sketch:unknown_graphics");
        requestMode = requestMode != null ? requestMode : IndirectPlanRequest.RequestMode.INDIRECT;
        submission = submission != null ? submission : DrawPlan.DrawSubmission.DIRECT_BATCH;
        reason = reason != null ? reason : "";
    }
}

