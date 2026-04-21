package rogo.sketch.vanilla;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import rogo.sketch.core.api.LevelPipelineProvider;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.vanilla.event.MinecraftHostEventContracts;

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

    public static void enterWorld() {
        enterWorld(Minecraft.getInstance().level);
    }

    public static void enterWorld(ClientLevel level) {
        GraphicsPipeline<?> pipeline = ((LevelPipelineProvider) Minecraft.getInstance().levelRenderer).getGraphicsPipeline();
        pipeline.enterWorld();
        MinecraftHostAdapter.getInstance().onWorldEnter(pipeline, level);
        pipeline.extensionHost().objectLifecycleEventBus().post(
                MinecraftHostEventContracts.WORLD_ENTER,
                new MinecraftHostEventContracts.WorldEnterEvent(pipeline, level));
    }

    public static void leaveWorld() {
        leaveWorld(Minecraft.getInstance().level);
    }

    public static void leaveWorld(ClientLevel level) {
        GraphicsPipeline<?> pipeline = ((LevelPipelineProvider) Minecraft.getInstance().levelRenderer).getGraphicsPipeline();
        MinecraftHostAdapter.getInstance().onWorldLeave(pipeline);
        pipeline.leaveWorld();
        pipeline.extensionHost().objectLifecycleEventBus().post(
                MinecraftHostEventContracts.WORLD_LEAVE,
                new MinecraftHostEventContracts.WorldLeaveEvent(pipeline, level));
    }

    public static void onResourceReload() {
        GraphicsPipeline<?> pipeline = ((LevelPipelineProvider) Minecraft.getInstance().levelRenderer).getGraphicsPipeline();
        pipeline.onResourceReload();
        pipeline.extensionHost().objectLifecycleEventBus().post(
                MinecraftHostEventContracts.RESOURCE_RELOAD,
                new MinecraftHostEventContracts.ResourceReloadEvent(pipeline));
    }

    public static void shutdown() {
        ((LevelPipelineProvider) Minecraft.getInstance().levelRenderer).getGraphicsPipeline().shutdown();
    }
}
