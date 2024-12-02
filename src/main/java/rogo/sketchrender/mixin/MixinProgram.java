package rogo.sketchrender.mixin;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rogo.sketchrender.shader.ShaderModifier;

import java.io.InputStream;
import java.util.List;

@Mixin(Program.class)
public abstract class MixinProgram {

    @Inject(method = "compileShaderInternal", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/preprocessor/GlslPreprocessor;process(Ljava/lang/String;)Ljava/util/List;"))
    private static void beforeCompileShaderInternal(Program.Type type, String shader, InputStream p_166615_, String source, GlslPreprocessor p_166617_, CallbackInfoReturnable<Integer> cir) {
        ShaderModifier.currentProgram = source + ":" + shader;
        ShaderModifier.currentType = type;
    }

    @ModifyArg(method = "compileShaderInternal", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/preprocessor/GlslPreprocessor;process(Ljava/lang/String;)Ljava/util/List;"))
    private static String onCompileShaderInternal(String string) {
        List<ShaderModifier> list = ShaderModifier.getTargetModifier();
        for (ShaderModifier modifier : list) {
            string = modifier.applyModifications(string);
        }
        ShaderModifier.currentProgram = "";
        ShaderModifier.currentType = null;
        return string;
    }
}
