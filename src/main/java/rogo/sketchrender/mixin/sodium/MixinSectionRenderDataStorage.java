package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.arena.GlBufferSegment;
import me.jellysquid.mods.sodium.client.gl.util.VertexRange;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.compat.sodium.SectionData;
import rogo.sketchrender.compat.sodium.RegionMeshManager;
import rogo.sketchrender.compat.sodium.SectionDataUtil;
import rogo.sketchrender.shader.uniform.SSBO;

@Mixin(value = SectionRenderDataStorage.class, remap = false)
public class MixinSectionRenderDataStorage implements SectionData {
    @Shadow
    @Final
    private long pMeshDataArray;

    @Shadow
    @Final
    private GlBufferSegment[] allocations;
    private SSBO meshData;
    private RenderRegion region;
    private int passIndex;
    private long passOffset;
    private int indexOffset;

    private final int[] sectionFacingCount = new int[256];
    private int totalFacingCount = 0;

    @Inject(method = "setMeshes", at = @At(value = "RETURN"), remap = false)
    private void endSetMeshes(int localSectionIndex, GlBufferSegment allocation, GlBufferSegment indexAllocation, VertexRange[] ranges, CallbackInfo ci) {
        totalFacingCount = SectionDataUtil.setMeshes(this.pMeshDataArray, localSectionIndex, meshData, indexOffset, sectionFacingCount, passIndex, region, passOffset);
    }

    @Inject(method = "updateMeshes", at = @At(value = "RETURN"), remap = false)
    private void endSetMeshes(int sectionIndex, CallbackInfo ci) {
        SectionDataUtil.copySectionMesh(this.pMeshDataArray, sectionIndex, meshData, passIndex, region, passOffset, indexOffset);
        totalFacingCount = SectionDataUtil.setMeshes(this.pMeshDataArray, sectionIndex, meshData, indexOffset, sectionFacingCount, passIndex, region, passOffset);
    }

    @Inject(method = "replaceIndexBuffer", at = @At(value = "RETURN"), remap = false)
    private void endReplaceIndexBuffer(int localSectionIndex, GlBufferSegment indexAllocation, CallbackInfo ci) {
        SectionDataUtil.copySectionMesh(this.pMeshDataArray, localSectionIndex, meshData, passIndex, region, passOffset, indexOffset);
    }

    @Override
    public void setMeshData(SSBO meshData, RenderRegion region, int passIndex) {
        this.meshData = meshData;
        this.region = region;

        this.passIndex = passIndex;
        this.passOffset = 256L * RegionMeshManager.SECTION_DATA_SIZE * passIndex;
        this.indexOffset = 256 * passIndex;
    }

    @Override
    public int facingCount() {
        return totalFacingCount;
    }

    @Inject(method = "removeMeshes", at = @At(value = "RETURN"), remap = false)
    private void endRemoveMeshes(int localSectionIndex, CallbackInfo ci) {
        totalFacingCount = SectionDataUtil.setMeshes(this.pMeshDataArray, localSectionIndex, meshData, indexOffset, sectionFacingCount, passIndex, region, passOffset);
    }
}