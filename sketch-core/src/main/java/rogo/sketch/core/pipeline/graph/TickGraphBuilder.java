package rogo.sketch.core.pipeline.graph;

import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;

/**
 * Builder for the tick lifecycle graph.
 *
 * @param <C> concrete render context type
 */
public final class TickGraphBuilder<C extends RenderContext> {
    private final GraphicsPipeline<C> pipeline;
    private final RenderGraphBuilder<C> preTickBuilder;
    private final RenderGraphBuilder<C> postTickBuilder;

    public TickGraphBuilder(GraphicsPipeline<C> pipeline) {
        this.pipeline = pipeline;
        this.preTickBuilder = new RenderGraphBuilder<>(pipeline);
        this.postTickBuilder = new RenderGraphBuilder<>(pipeline);
    }

    public TickGraphBuilder<C> addPreTickPass(PipelinePass<C> pass, String... dependsOn) {
        preTickBuilder.addPass(pass, dependsOn);
        return this;
    }

    public TickGraphBuilder<C> addPostTickPass(PipelinePass<C> pass, String... dependsOn) {
        postTickBuilder.addPass(pass, dependsOn);
        return this;
    }

    public boolean hasPreTickPass(String name) {
        return preTickBuilder.hasPass(name);
    }

    public boolean hasPostTickPass(String name) {
        return postTickBuilder.hasPass(name);
    }

    public GraphicsPipeline<C> pipeline() {
        return pipeline;
    }

    public CompiledTickGraph<C> compile() {
        return new CompiledTickGraph<>(preTickBuilder.compile(), postTickBuilder.compile(), pipeline);
    }
}
