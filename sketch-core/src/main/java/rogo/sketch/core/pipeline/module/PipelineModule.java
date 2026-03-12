package rogo.sketch.core.pipeline.module;

import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.graph.RenderGraphBuilder;

/**
 * Lifecycle interface for pipeline modules.
 * <p>
 * A module is a self-contained subsystem (e.g. Transform, Culling) that:
 * <ul>
 *   <li>Initializes with the pipeline</li>
 *   <li>Contributes passes to the render graph</li>
 *   <li>Optionally handles graphics instance attachment via {@link GraphicsModule}</li>
 *   <li>Cleans up when the pipeline shuts down</li>
 * </ul>
 * </p>
 */
public interface PipelineModule {

    /**
     * Unique name of this module.
     */
    String name();

    /**
     * Called once during pipeline initialization.
     *
     * @param pipeline The graphics pipeline
     */
    void initialize(GraphicsPipeline<?> pipeline);

    /**
     * Contribute passes to the render graph.
     * <p>
     * Called during graph compilation. The module should add its passes
     * (with dependency declarations) to the builder.
     * </p>
     *
     * @param builder The render graph builder
     * @param <C>     Concrete RenderContext type
     */
    <C extends RenderContext> void contributeToGraph(RenderGraphBuilder<C> builder);

    /**
     * Called when the pipeline is shutting down. Release resources here.
     */
    default void cleanup() {}

    /**
     * Priority for module initialization order (lower = earlier).
     * Default is 1000.
     */
    default int priority() { return 1000; }
}

