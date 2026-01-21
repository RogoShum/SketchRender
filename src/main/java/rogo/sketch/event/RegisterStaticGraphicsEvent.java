package rogo.sketch.event;

import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.render.pipeline.GraphicsPipeline;
import rogo.sketch.render.pipeline.PipelineType;
import rogo.sketch.render.pipeline.RenderParameter;
import rogo.sketch.render.pipeline.RenderSetting;
import rogo.sketch.util.KeyId;

public class RegisterStaticGraphicsEvent {
    private final GraphicsPipeline<?> pipeline;

    public RegisterStaticGraphicsEvent(GraphicsPipeline<?> pipeline) {
        this.pipeline = pipeline;
    }

    public void register(KeyId stage, Graphics graphics, RenderParameter renderParameter) {
        this.pipeline.addGraphInstance(stage, graphics, renderParameter);
    }

    public void register(KeyId stage, Graphics graphics, RenderParameter renderParameter, KeyId pipelineType) {
        this.pipeline.addGraphInstance(stage, graphics, renderParameter, pipelineType);
    }

    public void register(KeyId stageId, Graphics graph, RenderParameter renderParameter, KeyId pipelineType, KeyId containerType) {
        this.pipeline.addGraphInstance(stageId, graph, renderParameter, pipelineType, containerType);
    }
}