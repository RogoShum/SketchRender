package rogo.sketch.event;

import rogo.sketch.event.bridge.RegistryEvent;
import rogo.sketch.render.GraphicsPipeline;
import rogo.sketch.render.RenderContext;

/**
 * Event fired when a GraphicsPipeline is initialized
 * Compatible with the existing EventBusBridge system
 */
public class GraphicsPipelineInitEvent implements RegistryEvent {
    private final GraphicsPipeline<?> pipeline;
    private final InitPhase phase;
    
    public GraphicsPipelineInitEvent(GraphicsPipeline<?> pipeline, InitPhase phase) {
        this.pipeline = pipeline;
        this.phase = phase;
    }
    
    public GraphicsPipeline<?> getPipeline() {
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
