package rogo.sketch.event;

import rogo.sketch.render.GraphicsPipeline;
import rogo.sketch.render.RenderContext;

/**
 * Event fired when a GraphicsPipeline is initialized
 * Compatible with the existing EventBusBridge system
 */
public class GraphicsPipelineInitEvent<C extends RenderContext> {
    private final GraphicsPipeline<C> pipeline;
    private final InitPhase phase;
    
    public GraphicsPipelineInitEvent(GraphicsPipeline<C> pipeline, InitPhase phase) {
        this.pipeline = pipeline;
        this.phase = phase;
    }
    
    public GraphicsPipeline<C> getPipeline() {
        return pipeline;
    }
    
    public InitPhase getPhase() {
        return phase;
    }
    
    /**
     * Phases of pipeline initialization
     */
    public enum InitPhase {
        /**
         * Early initialization - register core stages
         */
        EARLY,
        
        /**
         * Normal initialization - register most stages and instances
         */
        NORMAL,
        
        /**
         * Late initialization - register stages that depend on others
         */
        LATE
    }
}
