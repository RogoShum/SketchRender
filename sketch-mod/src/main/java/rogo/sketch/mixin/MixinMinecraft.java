package rogo.sketch.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketch.SketchRender;
import rogo.sketch.feature.culling.CullingStateManager;
import rogo.sketch.profiler.Profiler;
import rogo.sketch.vanilla.MinecraftRenderStages;
import rogo.sketch.vanilla.PipelineUtil;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "runTick", at = @At(value = "RETURN"))
    public void afterRunTick(boolean p_91384_, CallbackInfo ci) {
        PipelineUtil.pipeline().renderStagesAfter(MinecraftRenderStages.RENDER_END.getIdentifier());
        Profiler.get().pop("runTick");
        Profiler.get().close();
    }

    @Inject(method = "runTick", at = @At(value = "HEAD"))
    public void beforeRunTick(boolean p_91384_, CallbackInfo ci) {
        PipelineUtil.pipeline().renderStateManager().reset();
        PipelineUtil.pipeline().renderStagesBefore(MinecraftRenderStages.RENDER_START.getIdentifier());
        Profiler.get().open();
        Profiler.get().push("runTick");
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(II)V"))
    public void beforeBlitToScreen(boolean p_91384_, CallbackInfo ci) {
        Profiler.get().push("blit_screen");
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(II)V", shift = At.Shift.AFTER))
    public void afterBlitToScreen(boolean p_91384_, CallbackInfo ci) {
        Profiler.get().pop("blit_screen");
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay()V"))
    public void beforeUpdateDisplay(boolean p_91384_, CallbackInfo ci) {
        Profiler.get().push("update_display");
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay()V", shift = At.Shift.AFTER))
    public void afterUpdateDisplay(boolean p_91384_, CallbackInfo ci) {
        Profiler.get().pop("update_display");
    }

    @Inject(method = "setLevel", at = @At(value = "HEAD"))
    public void onJoinWorld(ClientLevel world, CallbackInfo ci) {
        CullingStateManager.onWorldReload(world);
        SketchRender.getShaderManager().resetShader(Minecraft.getInstance().getResourceManager());
    }
}