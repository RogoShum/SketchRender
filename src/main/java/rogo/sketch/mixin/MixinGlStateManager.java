package rogo.sketch.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public abstract class MixinGlStateManager {
    @Inject(method = "_glBindBuffer", at = @At(value = "HEAD"))
    private static void bindBuffer(int buffer, int handle, CallbackInfo ci) {

    }
}
