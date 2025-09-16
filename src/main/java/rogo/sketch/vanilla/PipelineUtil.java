package rogo.sketch.vanilla;

import net.minecraft.client.Minecraft;
import rogo.sketch.api.LevelPipelineProvider;
import rogo.sketch.render.pipeline.GraphicsPipeline;
import rogo.sketch.render.RenderHelper;

public class PipelineUtil {
    private static final GraphicsPipeline<?> pipeline = ((LevelPipelineProvider) Minecraft.getInstance().levelRenderer).getGraphicsPipeline();
    private static final RenderHelper renderHelper = new RenderHelper(pipeline);

    public static RenderHelper renderHelper() {
        return renderHelper;
    }

    public static GraphicsPipeline<?> pipeline() {
        return pipeline;
    }
}