package rogo.sketch.backend.opengl;

import rogo.sketch.core.backend.SubmissionScheduler;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;

final class OpenGLSubmissionScheduler implements SubmissionScheduler {
    private volatile FrameExecutionPlan installedExecutionPlan = FrameExecutionPlan.empty();

    @Override
    public void installExecutionPlan(FrameExecutionPlan plan) {
        installedExecutionPlan = plan != null ? plan : FrameExecutionPlan.empty();
    }

    FrameExecutionPlan installedExecutionPlan() {
        return installedExecutionPlan;
    }
}
