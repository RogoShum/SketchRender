package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.arena.staging.StagingBuffer;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rogo.sketchrender.compat.sodium.RenderSectionManagerGetter;
import rogo.sketchrender.compat.sodium.SodiumSectionAsyncUtil;

@Mixin(RenderRegionManager.class)
public abstract class MixinRenderRegionManager {
    @Inject(method = "createForChunk", at = @At("RETURN"), remap = false)
    private void onInit(int chunkX, int chunkY, int chunkZ, CallbackInfoReturnable<RenderRegion> cir) {
        RenderSectionManagerGetter.getChunkData().addRenderRegion(cir.getReturnValue());
    }
}
