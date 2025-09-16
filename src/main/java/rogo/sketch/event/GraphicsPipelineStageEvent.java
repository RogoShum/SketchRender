package rogo.sketch.event;

import rogo.sketch.render.pipeline.GraphicsPipeline;
import rogo.sketch.render.pipeline.RenderContext;
import rogo.sketch.util.Identifier;

public class GraphicsPipelineStageEvent<C extends RenderContext> {
    private final GraphicsPipeline<C> pipeline;
    private final C context;
    private final Identifier stage;
    private final Phase phase;

    public GraphicsPipelineStageEvent(GraphicsPipeline<C> pipeline, Identifier stage, C context, Phase phase) {
        this.pipeline = pipeline;
        this.context = context;
        this.stage = stage;
        this.phase = phase;
    }

    public GraphicsPipeline<?> getPipeline() {
        return pipeline;
    }

    public C getContext() {
        return context;
    }

    public Identifier getStage() {
        return stage;
    }

    public Phase getPhase() {
        return phase;
    }

    public enum Phase {
        PRE,
        POST
    }
}