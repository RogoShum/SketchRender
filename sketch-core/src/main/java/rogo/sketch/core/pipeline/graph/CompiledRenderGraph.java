package rogo.sketch.core.pipeline.graph;

import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;

import java.util.Collections;
import java.util.List;

/**
 * Immutable, topologically-sorted render graph ready for execution.
 * <p>
 * Produced by {@link RenderGraphBuilder#compile()}. The scheduler
 * iterates the sorted pass list and dispatches each pass to the
 * correct thread domain.
 * </p>
 *
 * @param <C> Concrete RenderContext type
 */
public final class CompiledRenderGraph<C extends RenderContext> {
    private final List<PassNode<C>> sortedPasses;
    private final GraphicsPipeline<C> pipeline;

    CompiledRenderGraph(List<PassNode<C>> sortedPasses, GraphicsPipeline<C> pipeline) {
        this.sortedPasses = Collections.unmodifiableList(sortedPasses);
        this.pipeline = pipeline;
    }

    /**
     * Get all passes in topologically-sorted execution order.
     */
    public List<PassNode<C>> sortedPasses() {
        return sortedPasses;
    }

    /**
     * Get the pipeline this graph was compiled for.
     */
    public GraphicsPipeline<C> pipeline() {
        return pipeline;
    }

    /**
     * Get the number of passes.
     */
    public int passCount() {
        return sortedPasses.size();
    }
}

