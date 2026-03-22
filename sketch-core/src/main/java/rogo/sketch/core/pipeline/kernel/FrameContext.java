package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;

/**
 * Immutable snapshot of per-frame state, passed to every pass during execution.
 *
 * @param <C> Concrete RenderContext type
 */
public class FrameContext<C extends RenderContext> {
    private final GraphicsPipeline<C> pipeline;
    private final PipelineKernel<C> kernel;
    private final C renderContext;
    private final long frameNumber;

    public FrameContext(GraphicsPipeline<C> pipeline, PipelineKernel<C> kernel, C renderContext,
                        long frameNumber) {
        this.pipeline = pipeline;
        this.kernel = kernel;
        this.renderContext = renderContext;
        this.frameNumber = frameNumber;
    }

    public GraphicsPipeline<C> pipeline() { return pipeline; }
    public PipelineKernel<C> kernel() { return kernel; }
    public C renderContext() { return renderContext; }
    public long frameNumber() { return frameNumber; }
}
