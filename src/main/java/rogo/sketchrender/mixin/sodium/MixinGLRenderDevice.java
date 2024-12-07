package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.device.GLRenderDevice;
import me.jellysquid.mods.sodium.client.gl.device.MultiDrawBatch;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlIndexType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.api.TessellationDevice;
import rogo.sketchrender.culling.ChunkRenderMixinHook;

@Mixin(GLRenderDevice.class)
public class MixinGLRenderDevice implements TessellationDevice {
    @Shadow(remap = false)
    private GlTessellation activeTessellation;

    @Override
    public GlTessellation getTessellation() {
        return activeTessellation;
    }


    @Mixin(targets = "me.jellysquid.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList")
    public abstract static class MixinDefaultChunkRenderer {
        @Inject(remap = false, method = "multiDrawElementsBaseVertex", at = @At(value = "HEAD"), cancellable = true)
        private void onExecuteDrawBatch(MultiDrawBatch batch, GlIndexType indexType, CallbackInfo ci) {
            SketchRender.TIMER.start("multiDrawElementsBaseVertex");
            ChunkRenderMixinHook.onMultiDrawElementsBaseVertex(batch, indexType, ci);
        }

        @Inject(remap = false, method = "multiDrawElementsBaseVertex", at = @At(value = "RETURN"))
        private void endExecuteDrawBatch(MultiDrawBatch batch, GlIndexType indexType, CallbackInfo ci) {
            SketchRender.TIMER.end("multiDrawElementsBaseVertex");
        }
    }
}
