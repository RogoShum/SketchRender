package rogo.sketchrender.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.SketchRender;

@Mixin(RenderSystem.class)
public class MixinRenderSystem {
    @Inject(method = "flipFrame", at = @At(value = "HEAD"))
    private static void prevFlipFrame(long p_69496_, CallbackInfo ci) {
        SketchRender.COMMAND_TIMER.start("glfwSwapBuffers");
    }

    @Inject(method = "flipFrame", at = @At(value = "RETURN"))
    private static void postFlipFrame(long p_69496_, CallbackInfo ci) {
        SketchRender.COMMAND_TIMER.end("glfwSwapBuffers");
    }
}
