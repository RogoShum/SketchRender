package rogo.sketch.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.arena.staging.StagingBuffer;
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
import rogo.sketch.Config;
import rogo.sketch.compat.sodium.*;
import rogo.sketch.compat.sodium.api.ExtraRenderRegion;
import rogo.sketch.compat.sodium.api.ResourceChecker;

import java.util.Map;

@Mixin(value = RenderRegion.class, remap = false)
public abstract class MixinRenderRegion implements ExtraRenderRegion {
    @Shadow(remap = false)
    @Final
    private RenderSection[] sections;

    @Shadow
    @Final
    private Map<TerrainRenderPass, SectionRenderDataStorage> sectionRenderData;

    @Shadow
    @Final
    private StagingBuffer stagingBuffer;

    @Inject(method = "getSection", at = @At("HEAD"), cancellable = true, remap = false)
    private void onGetSection(int id, CallbackInfoReturnable<RenderSection> cir) {
        if (SodiumSectionAsyncUtil.RENDERING_ENTITIES && this.sections[id] == null)
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
        MeshResource.removeRegion((RenderRegion) (Object) this);
    }

    @Override
    public void refreshSectionData() {
        for (SectionRenderDataStorage storage : this.sectionRenderData.values()) {
            storage.delete();
        }
        this.sectionRenderData.clear();
    }

    @Override
    public boolean disposed() {
        if (this.stagingBuffer instanceof ResourceChecker checker) {
            return checker.disposed();
        }

        return false;
    }
}