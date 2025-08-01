package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rogo.sketchrender.api.Config;
import rogo.sketchrender.compat.sodium.ExtraRenderRegion;
import rogo.sketchrender.compat.sodium.MeshUniform;
import rogo.sketchrender.compat.sodium.RegionMeshDataStorage;
import rogo.sketchrender.compat.sodium.SodiumSectionAsyncUtil;

import java.util.Iterator;
import java.util.Map;

@Mixin(value = RenderRegion.class, remap = false)
public abstract class MixinRenderRegion implements ExtraRenderRegion {
    @Shadow(remap = false)
    @Final
    private RenderSection[] sections;

    @Shadow
    @Final
    private Map<TerrainRenderPass, SectionRenderDataStorage> sectionRenderData;

    @Inject(method = "getSection", at = @At("HEAD"), cancellable = true, remap = false)
    private void onGetSection(int id, CallbackInfoReturnable<RenderSection> cir) {
        if (SodiumSectionAsyncUtil.renderingEntities && this.sections[id] == null)
            cir.setReturnValue(new RenderSection((RenderRegion) (Object) this, 0, 0, 0));
    }

    @Inject(method = "createStorage", at = @At(value = "RETURN"), cancellable = true)
    private void onGetSection(TerrainRenderPass pass, CallbackInfoReturnable<SectionRenderDataStorage> cir) {
        if (Config.getCullChunk() && !(cir.getReturnValue() instanceof RegionMeshDataStorage)) {
            int layer = 0;

            if (pass == DefaultTerrainRenderPasses.TRANSLUCENT) {
                layer = 2;
            }

            if (pass == DefaultTerrainRenderPasses.CUTOUT) {
                layer = 1;
            }

            if (cir.getReturnValue() != null) {
                cir.getReturnValue().delete();
            }

            SectionRenderDataStorage storage = new RegionMeshDataStorage((RenderRegion) (Object) this, layer);
            this.sectionRenderData.put(pass, storage);
            cir.setReturnValue(storage);
        }
    }

    @Inject(method = "delete", at = @At("RETURN"), remap = false)
    private void onDelete(CommandList commandList, CallbackInfo ci) {
        MeshUniform.removeRegion((RenderRegion) (Object) this);
    }

    @Override
    public void refreshSectionData() {
        for (SectionRenderDataStorage storage : this.sectionRenderData.values()) {
            storage.delete();
        }
        this.sectionRenderData.clear();
    }
}