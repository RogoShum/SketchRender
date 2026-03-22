package rogo.sketch.core.pipeline.graph;

import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;

/**
 * Compiled tick graph containing separate segments for pre-tick and post-tick work.
 *
 * @param <C> concrete render context type
 */
public record CompiledTickGraph<C extends RenderContext>(
        CompiledRenderGraph<C> preTickGraph,
        CompiledRenderGraph<C> postTickGraph,
        GraphicsPipeline<C> pipeline
) {
}
