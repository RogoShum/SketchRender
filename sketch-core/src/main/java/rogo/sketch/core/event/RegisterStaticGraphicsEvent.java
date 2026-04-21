package rogo.sketch.core.event;

import rogo.sketch.core.graphics.ecs.GraphicsEntityBlueprint;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.util.KeyId;

public class RegisterStaticGraphicsEvent {
    private final GraphicsPipeline<?> pipeline;

    public RegisterStaticGraphicsEvent(GraphicsPipeline<?> pipeline) {
        this.pipeline = pipeline;
    }

    public void registerCompute(KeyId stage, GraphicsEntityBlueprint blueprint) {
        pipeline.spawnGraphicsEntity(blueprint);
    }

    public void registerFunction(KeyId stage, GraphicsEntityBlueprint blueprint) {
        pipeline.spawnGraphicsEntity(blueprint);
    }

    public void register(KeyId stage, GraphicsEntityBlueprint blueprint) {
        pipeline.spawnGraphicsEntity(blueprint);
    }
}
