package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.arena.GlBufferSegment;
import me.jellysquid.mods.sodium.client.gl.util.VertexRange;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.api.DataStorage;
import rogo.sketchrender.shader.uniform.SSBO;

@Mixin(SectionRenderDataStorage.class)
public abstract class MixinSectionRenderDataStorage implements DataStorage {

    @Shadow
    @Final
    private long pMeshDataArray;
    private SSBO meshSSBO;

    @Inject(method = "<init>", at = @At(value = "RETURN"), remap = false)
    private void onInit(CallbackInfo ci) {
        meshSSBO = new SSBO(256, 64, this.pMeshDataArray);
    }

    @Inject(method = "setMeshes", at = @At(value = "RETURN"), remap = false)
    private void endSetMeshes(int localSectionIndex, GlBufferSegment allocation, VertexRange[] ranges, CallbackInfo ci) {
        meshSSBO.resetUpload();
    }

    @Inject(method = "updateMeshes", at = @At(value = "RETURN"), remap = false)
    private void endSetMeshes(int sectionIndex, CallbackInfo ci) {
        meshSSBO.resetUpload();
    }

    @Override
    public void bindSSBO(int slot) {
        this.meshSSBO.bindShaderSlot(slot);
    }

    @Inject(method = "removeMeshes", at = @At(value = "RETURN"), remap = false)
    private void endRemoveMeshes(int localSectionIndex, CallbackInfo ci) {
        meshSSBO.resetUpload();
    }
}
