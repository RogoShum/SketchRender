package rogo.sketch.api;

import rogo.sketch.render.pipeline.GraphicsPipeline;

public interface LevelPipelineProvider {
    GraphicsPipeline<?> getGraphicsPipeline();
}