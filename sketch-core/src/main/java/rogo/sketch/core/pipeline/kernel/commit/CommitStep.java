package rogo.sketch.core.pipeline.kernel.commit;

import rogo.sketch.core.pipeline.RenderContext;

@FunctionalInterface
public interface CommitStep<C extends RenderContext> {
    boolean execute(FrameCommitPipeline.CommitContext<C> context);
}
