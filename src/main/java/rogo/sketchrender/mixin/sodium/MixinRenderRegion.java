package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.lwjgl.opengl.GL15;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rogo.sketchrender.api.RegionData;
import rogo.sketchrender.api.SectionData;
import rogo.sketchrender.compat.sodium.SodiumSectionAsyncUtil;
import rogo.sketchrender.compat.sodium.MeshUniform;
import rogo.sketchrender.shader.uniform.SSBO;

@Mixin(value = RenderRegion.class, remap = false)
public abstract class MixinRenderRegion implements RegionData {
    @Shadow(remap = false)
    @Final
    private RenderSection[] sections;

    private SSBO meshData;

    @Inject(method = "<init>", at = @At(value = "RETURN"), remap = false)
    private void onInit(CallbackInfo ci) {
        meshData = new SSBO(768, 64, GL15.GL_DYNAMIC_DRAW);
    }

    @Inject(method = "getSection", at = @At("HEAD"), cancellable = true, remap = false)
    private void onGetSection(int id, CallbackInfoReturnable<RenderSection> cir) {
        if (SodiumSectionAsyncUtil.renderingEntities && this.sections[id] == null)
            cir.setReturnValue(new RenderSection((RenderRegion) (Object) this, 0, 0, 0));
    }

    @Inject(method = "createStorage", at = @At(value = "RETURN", remap = false))
    private void onGetSection(TerrainRenderPass pass, CallbackInfoReturnable<SectionRenderDataStorage> cir) {
        if (cir.getReturnValue() instanceof SectionData sectionData) {
            int layer = 0;

            if (pass == DefaultTerrainRenderPasses.TRANSLUCENT) {
                layer = 2;
            }

            if (pass == DefaultTerrainRenderPasses.CUTOUT) {
                layer = 1;
            }

            sectionData.setMeshData(meshData, (RenderRegion) (Object)this, layer);
        }
    }

    @Inject(method = "delete", at = @At("HEAD"), remap = false)
    private void onDelete(CommandList commandList, CallbackInfo ci) {
        meshData.discard();
        MeshUniform.removeRegion((RenderRegion) (Object)this);
    }

    @Override
    public void bindMeshData(int slot) {
        meshData.bindShaderSlot(slot);
    }
}
