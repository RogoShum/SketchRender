package rogo.sketch.mixin.sodium;

import com.mojang.blaze3d.platform.GlDebug;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlDebug.class)
public class MixinGlDebug {
    @Inject(method = "enableDebugCallback", at = @At(value = "HEAD"), cancellable = true)
    private static void prevFlipFrame(int p_84050_, boolean p_84051_, CallbackInfo ci) {
        if (SodiumClientMod.options().performance.useNoErrorGLContext) {
            ci.cancel();
        }
    }

    @Inject(method = "printDebugLog", at = @At(value = "HEAD"), cancellable = true)
    private static void postFlipFrame(int p_84039_, int p_84040_, int p_84041_, int p_84042_, int p_84043_, long p_84044_, long p_84045_, CallbackInfo ci) {
        if (SodiumClientMod.options().performance.useNoErrorGLContext) {
            ci.cancel();
        }
    }
}