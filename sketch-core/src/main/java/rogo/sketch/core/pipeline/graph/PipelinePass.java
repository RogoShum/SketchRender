package rogo.sketch.core.pipeline.graph;

import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.kernel.FrameContext;
import rogo.sketch.core.pipeline.kernel.ThreadDomain;

/**
 * A single unit of work in the render graph.
 * <p>
 * Each pass declares:
 * <ul>
 *   <li>{@link #name()} — unique identifier for dependency references</li>
 *   <li>{@link #threadDomain()} — which thread this pass must run on</li>
 *   <li>{@link #execute(FrameContext)} — the actual work</li>
 * </ul>
 * </p>
 *
 * @param <C> Concrete RenderContext type
 */
public interface PipelinePass<C extends RenderContext> {

    /**
     * Unique name of this pass within the render graph.
     */
    String name();

    /**
     * The thread domain this pass requires.
     */
    ThreadDomain threadDomain();

    /**
     * Execute this pass.
     *
     * @param ctx Frame-scoped context with pipeline, render context, and blackboard
     */
    void execute(FrameContext<C> ctx);
}

