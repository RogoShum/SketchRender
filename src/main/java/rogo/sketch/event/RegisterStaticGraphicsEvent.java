package rogo.sketch.event;

import rogo.sketch.api.graphics.GraphicsInstance;
import rogo.sketch.render.GraphicsPipeline;
import rogo.sketch.render.RenderSetting;
import rogo.sketch.util.Identifier;

public class RegisterStaticGraphicsEvent {
    private final GraphicsPipeline<?> pipeline;

    public RegisterStaticGraphicsEvent(GraphicsPipeline<?> pipeline) {
        this.pipeline = pipeline;
    }

    public void register(Identifier stage, GraphicsInstance graphics, RenderSetting renderSetting) {
        this.pipeline.addGraphInstance(stage, graphics, renderSetting);
    }
}