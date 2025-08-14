package rogo.sketch.api;

import rogo.sketch.render.GraphicsPipeline;

public interface LevelPipeline {
    GraphicsPipeline<?> getGraphicsPipeline();
}