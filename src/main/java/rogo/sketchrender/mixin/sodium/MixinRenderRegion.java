package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.arena.staging.StagingBuffer;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rogo.sketchrender.compat.sodium.RenderSectionManagerGetter;
import rogo.sketchrender.compat.sodium.SodiumSectionAsyncUtil;

@Mixin(RenderRegion.class)
public abstract class MixinRenderRegion {
    @Shadow(remap = false)
    @Final
    private RenderSection[] sections;

    @Inject(method = "createStorage", at = @At("RETURN"), remap = false)
    private void onGetSection(TerrainRenderPass pass, CallbackInfoReturnable<SectionRenderDataStorage> cir) {
        RenderSectionManagerGetter.getChunkData().addStorage((RenderRegion) (Object) this, pass, cir.getReturnValue());
    }

    @Inject(method = "getSection", at = @At("HEAD"), cancellable = true, remap = false)
    private void onGetSection(int id, CallbackInfoReturnable<RenderSection> cir) {
        if (SodiumSectionAsyncUtil.renderingEntities && this.sections[id] == null)
            cir.setReturnValue(new RenderSection((RenderRegion) (Object) this, 0, 0, 0));
    }

    @Inject(method = "delete", at = @At("RETURN"), remap = false)
    private void onGetSection(CommandList commandList, CallbackInfo ci) {
        RenderSectionManagerGetter.getChunkData().deleteRegion((RenderRegion) (Object) this);
    }
}
