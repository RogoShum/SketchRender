package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.device.MultiDrawBatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rogo.sketchrender.api.IndirectDrawBatch;
import rogo.sketchrender.shader.IndirectCommandBuffer;

@Mixin(MultiDrawBatch.class)
public abstract class MixinMultiDrawBatch implements IndirectDrawBatch {
    @Inject(method = "getIndexBufferSize", at = @At("HEAD"), remap = false, cancellable = true)
    private void onSize(CallbackInfoReturnable<Integer> cir) {
        if (IndirectCommandBuffer.INSTANCE.maxElementCount > 0) {
            cir.setReturnValue(IndirectCommandBuffer.INSTANCE.maxElementCount);
        }
    }
}
