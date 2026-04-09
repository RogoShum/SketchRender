package rogo.sketch.vanilla;

import net.minecraft.client.Minecraft;
import rogo.sketch.core.api.LevelPipelineProvider;
import rogo.sketch.core.pipeline.GraphicsPipeline;

public class PipelineUtil {
    private static final GraphicsPipeline<?> pipeline = ((LevelPipelineProvider) Minecraft.getInstance().levelRenderer).getGraphicsPipeline();

    public static GraphicsPipeline<?> pipeline() {
        return pipeline;
    }
}