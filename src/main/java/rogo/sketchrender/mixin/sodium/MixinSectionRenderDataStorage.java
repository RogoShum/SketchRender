package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.arena.GlBufferSegment;
import me.jellysquid.mods.sodium.client.gl.util.VertexRange;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.api.SectionData;
import rogo.sketchrender.compat.sodium.MeshUniform;
import rogo.sketchrender.compat.sodium.RegionMeshManager;
import rogo.sketchrender.shader.uniform.SSBO;

import java.util.Arrays;

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

    @Inject(method = "setMeshes", at = @At("HEAD"))
    public void onSetMeshesHead(int localSectionIndex, GlBufferSegment allocation, GlBufferSegment indexAllocation, VertexRange[] ranges, CallbackInfo ci) {

    }

    @Inject(method = "setMeshes", at = @At(value = "RETURN"), remap = false)
    private void endSetMeshes(int localSectionIndex, GlBufferSegment allocation, GlBufferSegment indexAllocation, VertexRange[] ranges, CallbackInfo ci) {
        copySectionMesh(localSectionIndex);
        meshData.upload(localSectionIndex + indexOffset);

        long pMeshData = this.getDataPointer(localSectionIndex);
        int sliceMask = SectionRenderDataUnsafe.getSliceMask(pMeshData);
        int facingCount = Integer.bitCount(sliceMask);
        sectionFacingCount[localSectionIndex] = facingCount;
        totalFacingCount = Arrays.stream(sectionFacingCount).sum();
    }

    @Inject(method = "updateMeshes", at = @At(value = "RETURN"), remap = false)
    private void endSetMeshes(int sectionIndex, CallbackInfo ci) {
        copySectionMesh(sectionIndex);
        meshData.upload(sectionIndex + indexOffset);

        long pMeshData = this.getDataPointer(sectionIndex);
        int sliceMask = SectionRenderDataUnsafe.getSliceMask(pMeshData);
        int facingCount = Integer.bitCount(sliceMask);
        sectionFacingCount[sectionIndex] = facingCount;
        totalFacingCount = Arrays.stream(sectionFacingCount).sum();
    }

    @Inject(method = "replaceIndexBuffer", at = @At(value = "RETURN"), remap = false)
    private void endReplaceIndexBuffer(int localSectionIndex, GlBufferSegment indexAllocation, CallbackInfo ci) {
        copySectionMesh(localSectionIndex);
        meshData.upload(localSectionIndex + indexOffset);
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
        copySectionMesh(localSectionIndex);
        meshData.upload(localSectionIndex + indexOffset);

        long pMeshData = this.getDataPointer(localSectionIndex);
        int sliceMask = SectionRenderDataUnsafe.getSliceMask(pMeshData);
        int facingCount = Integer.bitCount(sliceMask);
        sectionFacingCount[localSectionIndex] = facingCount;
        totalFacingCount = Arrays.stream(sectionFacingCount).sum();
    }

    @Unique
    private void copySectionMesh(int index) {
        MemoryUtil.memCopy(this.pMeshDataArray + RegionMeshManager.SECTION_DATA_SIZE * index, meshData.getMemoryAddress() + passOffset + (RegionMeshManager.SECTION_DATA_SIZE * index), RegionMeshManager.SECTION_DATA_SIZE);
        MeshUniform.meshManager.copySectionData(region, passIndex, index, this.pMeshDataArray + RegionMeshManager.SECTION_DATA_SIZE * index);
    }

    @Unique
    private long getDataPointer(int sectionIndex) {
        return SectionRenderDataUnsafe.heapPointer(this.pMeshDataArray, sectionIndex);
    }
}