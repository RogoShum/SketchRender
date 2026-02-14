package rogo.sketch.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketch.profiler.Profiler;
import rogo.sketch.vanilla.MinecraftRenderStages;
import rogo.sketch.vanilla.PipelineUtil;

@Mixin(GameRenderer.class)
public abstract class MixinGameRender {

    @Inject(method = "render", at = @At(value = "HEAD"))
    public void onRenderHead(float p_109094_, long p_109095_, boolean p_109096_, CallbackInfo ci) {
        Profiler.get().start("game render");
    }

    @Inject(method = "render", at = @At(value = "RETURN"))
    public void onRenderReturn(float p_109094_, long p_109095_, boolean p_109096_, CallbackInfo ci) {
        Profiler.get().end("game render");
    }

    @Inject(method = "renderLevel", at = @At(value = "HEAD"))
    public void onRenderLevelHead(float p_109090_, long p_109091_, PoseStack p_109092_, CallbackInfo ci) {
        Profiler.get().endStart("pre level");
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V"))
    public void onRenderLevel(float p_109090_, long p_109091_, PoseStack p_109092_, CallbackInfo ci) {
        PipelineUtil.pipeline().renderStagesBetween(MinecraftRenderStages.LEVEL_END.getIdentifier(), MinecraftRenderStages.HAND.getIdentifier());
    }

    @Inject(method = "renderLevel", at = @At(value = "RETURN"))
    public void onRenderHand(float p_109090_, long p_109091_, PoseStack p_109092_, CallbackInfo ci) {
        PipelineUtil.pipeline().renderStagesBetween(MinecraftRenderStages.HAND.getIdentifier(), MinecraftRenderStages.POST_PROGRESS.getIdentifier());
        Profiler.get().endStart("game level");
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    public void onRenderPost(float p_109094_, long p_109095_, boolean p_109096_, CallbackInfo ci) {
        PipelineUtil.pipeline().renderStagesBetween(MinecraftRenderStages.POST_PROGRESS.getIdentifier(), MinecraftRenderStages.GUI.getIdentifier());
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Overlay;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    public void onGUI(float p_109094_, long p_109095_, boolean p_109096_, CallbackInfo ci) {
        Profiler.get().endStart("gui");
    }
}