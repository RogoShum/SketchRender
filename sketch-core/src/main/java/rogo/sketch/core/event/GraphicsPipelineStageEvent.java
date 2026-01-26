package rogo.sketch.core.event;

import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

public class GraphicsPipelineStageEvent<C extends RenderContext> {
    private final GraphicsPipeline<C> pipeline;
    private final C context;
    private final KeyId stage;
    private final Phase phase;

    public GraphicsPipelineStageEvent(GraphicsPipeline<C> pipeline, KeyId stage, C context, Phase phase) {
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

    public KeyId getStage() {
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