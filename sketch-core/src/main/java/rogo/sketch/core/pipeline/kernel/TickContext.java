package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;

/**
 * Tick-scoped execution context for tick graph passes.
 *
 * @param <C> concrete render context type
 */
public final class TickContext<C extends RenderContext> extends FrameContext<C> {
    private final long logicTick;

    public TickContext(GraphicsPipeline<C> pipeline, PipelineKernel<C> kernel, C renderContext,
                       long logicTick) {
        super(pipeline, kernel, renderContext, logicTick);
        this.logicTick = logicTick;
    }

    public long logicTick() {
        return logicTick;
    }

    @Override
    public long renderFrameEpoch() {
        return -1L;
    }

    @Override
    public long logicTickEpoch() {
        return logicTick;
    }
}
