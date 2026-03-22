package rogo.sketch.core.pipeline.graph.pass;

import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;

/**
 * Built-in post-tick sync pass that runs graphics tick work on the main thread.
 */
public class PostTickGraphicsPass<C extends RenderContext> implements PipelinePass<C> {
    public static final String NAME = "post_tick_graphics";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ThreadDomain threadDomain() {
        return ThreadDomain.SYNC;
    }

    @Override
    public void execute(FrameContext<C> ctx) {
        ctx.pipeline().tickGraphics();
    }
}
