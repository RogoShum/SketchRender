package rogo.sketchrender.mixin.sodium;

import com.mojang.blaze3d.shaders.Program;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.shader.ShaderModifier;

import java.util.List;

import static org.lwjgl.opengl.GL20.*;

@Mixin(targets = "me.jellysquid.mods.sodium.client.gl.shader.ShaderWorkarounds")
public abstract class MixinShaderWorkarounds {

    @Inject(remap = false, method = "safeShaderSource", at = @At(value = "HEAD"))
    private static void beforeCompileShaderInternal(int glId, CharSequence source, CallbackInfo ci) {
        ShaderModifier.currentProgram = "sodium:terrain";
        ShaderModifier.currentType = getShaderType(glId) == GL_VERTEX_SHADER ? Program.Type.VERTEX : Program.Type.FRAGMENT;
    }

    @ModifyArg(remap = false, method = "safeShaderSource", at = @At(value = "INVOKE", target = "Lorg/lwjgl/system/MemoryUtil;memUTF8(Ljava/lang/CharSequence;Z)Ljava/nio/ByteBuffer;"))
    private static CharSequence beforeCompileShaderInternal(CharSequence text) {
        List<ShaderModifier> list = ShaderModifier.getTargetModifier();
        for (ShaderModifier modifier : list) {
            text = modifier.applyModifications((String) text);
        }
        ShaderModifier.currentProgram = "";
        ShaderModifier.currentType = null;
        return text;
    }

    private static int getShaderType(int shaderHandle) {
        // 创建一个存储结果的变量
        int[] shaderType = new int[1];
        // 查询着色器的类型
        glGetShaderiv(shaderHandle, GL_SHADER_TYPE, shaderType);
        return shaderType[0];
    }
}
