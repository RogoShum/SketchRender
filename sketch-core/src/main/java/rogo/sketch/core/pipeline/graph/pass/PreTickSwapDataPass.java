package rogo.sketch.core.pipeline.graph.pass;

import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.PipelinePass;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;

/**
 * Built-in pre-tick pass that swaps graphics data after async tick work completes.
 */
public class PreTickSwapDataPass<C extends RenderContext> implements PipelinePass<C> {
    public static final String NAME = "pre_tick_swap_data";

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
        ctx.pipeline().swapGraphicsData();
    }
}
