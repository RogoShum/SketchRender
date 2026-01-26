package rogo.sketch.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.IOException;
import java.io.InputStream;

@Mixin(Program.class)
public abstract class MixinProgram {

    @Inject(method = "compileShaderInternal", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;glGetShaderi(II)I"), locals = LocalCapture.CAPTURE_FAILHARD)
    private static void atCompileShaderException(Program.Type p_166613_, String string, InputStream p_166615_, String p_166616_, GlslPreprocessor p_166617_, CallbackInfoReturnable<Integer> cir, String s, int i) throws IOException {
        if (GlStateManager.glGetShaderi(i, 35713) == 0) {
            String s1 = StringUtils.trim(GlStateManager.glGetShaderInfoLog(i, 32768));
            throw new IOException("Couldn't compile " + string + p_166613_.getExtension() + " program (" + p_166616_ + ", " + string + ") : " + s1);
        }
    }
}
