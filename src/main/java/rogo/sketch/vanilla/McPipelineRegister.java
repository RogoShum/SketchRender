package rogo.sketch.vanilla;

import net.minecraft.client.Minecraft;
import rogo.sketch.api.LevelPipeline;

public class McPipelineRegister {
    public static void initPipeline() {
        ((LevelPipeline) Minecraft.getInstance().levelRenderer).getGraphicsPipeline().initialize();
    }
}