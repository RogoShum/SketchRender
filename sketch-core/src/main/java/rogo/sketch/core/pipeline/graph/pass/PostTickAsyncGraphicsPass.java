package rogo.sketch.core.pipeline.graph.pass;

import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;

/**
 * Built-in post-tick async pass that runs graphics async tick work on the worker.
 */
public class PostTickAsyncGraphicsPass<C extends RenderContext> implements PipelinePass<C> {
    public static final String NAME = "post_tick_async_graphics";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ThreadDomain threadDomain() {
        return ThreadDomain.ASYNC;
    }

    @Override
    public void execute(FrameContext<C> ctx) {
        ctx.pipeline().asyncTickGraphics();
        ctx.pipeline().prepareNextFrameStageViews();
    }
}
