package rogo.sketchrender.mixin.sodium;

import com.mojang.blaze3d.systems.RenderSystem;
import me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.api.Config;

@Mixin(OcclusionCuller.class)
public abstract class MixinOcclusionCuller {

    @Inject(method = "findVisible", at = @At(value = "HEAD"), remap = false, cancellable = true)
    private void onFindVisible(OcclusionCuller.Visitor visitor, Viewport viewport, float searchDistance, boolean useOcclusionCulling, int frame, CallbackInfo ci) {
        if (Config.getAsyncChunkRebuild() && RenderSystem.isOnRenderThread()) {
            ci.cancel();
        }
    }
}