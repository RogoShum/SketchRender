package rogo.sketch.core.api;

import rogo.sketch.core.pipeline.GraphicsPipeline;

public interface LevelPipelineProvider {
    GraphicsPipeline<?> getGraphicsPipeline();
}