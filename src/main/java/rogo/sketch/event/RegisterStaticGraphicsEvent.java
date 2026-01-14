package rogo.sketch.event;

import rogo.sketch.api.graphics.Graphics;
import rogo.sketch.render.pipeline.GraphicsPipeline;
import rogo.sketch.render.pipeline.RenderSetting;
import rogo.sketch.util.Identifier;

public class RegisterStaticGraphicsEvent {
    private final GraphicsPipeline<?> pipeline;

    public RegisterStaticGraphicsEvent(GraphicsPipeline<?> pipeline) {
        this.pipeline = pipeline;
    }

    public void register(Identifier stage, Graphics graphics, RenderSetting renderSetting) {
        this.pipeline.addGraphInstance(stage, graphics, renderSetting);
    }
}