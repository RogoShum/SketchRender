package rogo.sketch.vanilla;

import net.minecraft.client.Minecraft;
import rogo.sketch.api.LevelPipelineProvider;

public class McPipelineRegister {
    public static void initPipeline() {
        ((LevelPipelineProvider) Minecraft.getInstance().levelRenderer).getGraphicsPipeline().initialize();
    }
}