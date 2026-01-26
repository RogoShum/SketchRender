package rogo.sketch.core.event;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

public class RegisterStaticGraphicsEvent {
    private final GraphicsPipeline<?> pipeline;

    public RegisterStaticGraphicsEvent(GraphicsPipeline<?> pipeline) {
        this.pipeline = pipeline;
    }

    public void registerCompute(KeyId stage, Graphics graphics) {
        this.pipeline.addCompute(stage, graphics);
    }

    public void registerFunction(KeyId stage, Graphics graphics) {
        this.pipeline.addFunction(stage, graphics);
    }

    public void register(KeyId stage, Graphics graphics, RenderParameter renderParameter) {
        this.pipeline.addGraphInstance(stage, graphics, renderParameter);
    }

    public void register(KeyId stage, Graphics graphics, RenderParameter renderParameter, PipelineType pipelineType) {
        this.pipeline.addGraphInstance(stage, graphics, renderParameter, pipelineType);
    }

    public void register(KeyId stageId, Graphics graph, RenderParameter renderParameter, PipelineType pipelineType, KeyId containerType) {
        this.pipeline.addGraphInstance(stageId, graph, renderParameter, pipelineType, containerType);
    }
}