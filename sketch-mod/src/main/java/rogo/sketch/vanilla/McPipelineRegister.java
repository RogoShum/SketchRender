package rogo.sketch.vanilla;

import net.minecraft.client.Minecraft;
import rogo.sketch.core.api.LevelPipelineProvider;

public class McPipelineRegister {
    public static void initPipeline() {
        ((LevelPipelineProvider) Minecraft.getInstance().levelRenderer).getGraphicsPipeline().initPipeline();
    }

    public static void initKernel() {
        ((LevelPipelineProvider) Minecraft.getInstance().levelRenderer).getGraphicsPipeline().initKernel();
    }

    public static void initGraphics() {
        ((LevelPipelineProvider) Minecraft.getInstance().levelRenderer).getGraphicsPipeline().initStaticGraphics();
    }
}