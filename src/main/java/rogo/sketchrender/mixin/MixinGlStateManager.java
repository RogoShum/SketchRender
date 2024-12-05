package rogo.sketchrender.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rogo.sketchrender.shader.ShaderModifier;

import java.io.InputStream;
import java.util.List;

@Mixin(GlStateManager.class)
public abstract class MixinGlStateManager {
    @Inject(method = "_glBindBuffer", at = @At(value = "HEAD"))
    private static void bindBuffer(int buffer, int handle, CallbackInfo ci) {

    }
}
