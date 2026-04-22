package rogo.sketch.core.pipeline.graph;

import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;

/**
 * Builder for the tick lifecycle graph.
 *
 * @param <C> concrete render context type
 */
public final class TickGraphBuilder<C extends RenderContext> {
    public record TickGraphContribution(
            java.util.List<String> preTickPassNames,
            java.util.List<String> postTickPassNames,
            java.util.List<String> postTickGlAsyncPassNames) {
        public TickGraphContribution {
            preTickPassNames = preTickPassNames != null ? java.util.List.copyOf(preTickPassNames) : java.util.List.of();
            postTickPassNames = postTickPassNames != null ? java.util.List.copyOf(postTickPassNames) : java.util.List.of();
            postTickGlAsyncPassNames = postTickGlAsyncPassNames != null ? java.util.List.copyOf(postTickGlAsyncPassNames) : java.util.List.of();
        }
    }

    private final GraphicsPipeline<C> pipeline;
    private final RenderGraphBuilder<C> preTickBuilder;
    private final RenderGraphBuilder<C> postTickBuilder;
    private final RenderGraphBuilder<C> postTickGlAsyncBuilder;

    public TickGraphBuilder(GraphicsPipeline<C> pipeline) {
        this.pipeline = pipeline;
        this.preTickBuilder = new RenderGraphBuilder<>(pipeline);
        this.postTickBuilder = new RenderGraphBuilder<>(pipeline);
        this.postTickGlAsyncBuilder = new RenderGraphBuilder<>(pipeline);
    }

    public TickGraphBuilder<C> addPreTickPass(PipelinePass<C> pass, String... dependsOn) {
        preTickBuilder.addPass(pass, dependsOn);
        return this;
    }

    public TickGraphBuilder<C> addPostTickPass(PipelinePass<C> pass, String... dependsOn) {
        postTickBuilder.addPass(pass, dependsOn);
        return this;
    }

    public TickGraphBuilder<C> addPostTickGlAsyncPass(PipelinePass<C> pass, String... dependsOn) {
        postTickGlAsyncBuilder.addPass(pass, dependsOn);
        return this;
    }

    public boolean hasPreTickPass(String name) {
        return preTickBuilder.hasPass(name);
    }

    public boolean hasPostTickPass(String name) {
        return postTickBuilder.hasPass(name);
    }

    public boolean hasPostTickGlAsyncPass(String name) {
        return postTickGlAsyncBuilder.hasPass(name);
    }

    public TickGraphContribution contribution() {
        return new TickGraphContribution(
                preTickBuilder.passNames(),
                postTickBuilder.passNames(),
                postTickGlAsyncBuilder.passNames());
    }

    public GraphicsPipeline<C> pipeline() {
        return pipeline;
    }

    public CompiledTickGraph<C> compile() {
        return new CompiledTickGraph<>(
                preTickBuilder.compile(),
                postTickBuilder.compile(),
                postTickGlAsyncBuilder.compile(),
                pipeline);
    }
}
