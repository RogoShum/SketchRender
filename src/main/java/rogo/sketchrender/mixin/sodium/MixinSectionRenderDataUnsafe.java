package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.arena.GlBufferSegment;
import me.jellysquid.mods.sodium.client.gl.util.VertexRange;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.compat.sodium.RenderSectionManagerGetter;
import rogo.sketchrender.culling.ChunkDataStorage;

@Mixin(SectionRenderDataUnsafe.class)
public abstract class MixinSectionRenderDataUnsafe {

    @Inject(method = "setElementCount", at = @At(value = "HEAD"), remap = false)
    private static void onSetElementCount(long ptr, int facing, int value, CallbackInfo ci) {
        RenderSectionManagerGetter.getChunkData().setElementCount(facing, value);
    }

    @Inject(method = "setSliceMask", at = @At(value = "HEAD"), remap = false)
    private static void endSetSliceMask(long ptr, int value, CallbackInfo ci) {
        RenderSectionManagerGetter.getChunkData().setSliceMask(value);
    }

    @Inject(method = "setVertexOffset", at = @At(value = "HEAD"), remap = false)
    private static void onSetVertexOffset(long ptr, int facing, int value, CallbackInfo ci) {
        RenderSectionManagerGetter.getChunkData().setVertexOffset(facing, value);
    }
}
