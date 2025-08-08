package rogo.sketch.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketch.SketchRender;
import rogo.sketch.feature.culling.CullingStateManager;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(FJZ)V", shift = At.Shift.AFTER))
    public void afterRunTick(boolean p_91384_, CallbackInfo ci) {
        CullingStateManager.onProfilerPopPush("afterRunTick");
    }

    @Inject(method = "runTick", at = @At(value = "HEAD"))
    public void beforeRunTick(boolean p_91384_, CallbackInfo ci) {
        CullingStateManager.onProfilerPopPush("beforeRunTick");
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay()V"))
    public void timerUpdateDisplay(boolean p_91384_, CallbackInfo ci) {
    }

    @Inject(method = "setLevel", at = @At(value = "HEAD"))
    public void onJoinWorld(ClientLevel world, CallbackInfo ci) {
        CullingStateManager.onWorldUnload(world);
        SketchRender.getShaderManager().resetShader(Minecraft.getInstance().getResourceManager());
    }
}