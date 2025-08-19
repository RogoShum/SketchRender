package rogo.sketch.compat.sodium;

import me.jellysquid.mods.sodium.client.gl.arena.GlBufferSegment;
import me.jellysquid.mods.sodium.client.gl.util.VertexRange;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;

public class RegionMeshDataStorage extends SectionRenderDataStorage {
    private final RenderRegion region;
    private final int passIndex;

    private final int[] sectionFacingCount = new int[256];
    private int totalFacingCount = 0;

    public RegionMeshDataStorage(RenderRegion region, int passIndex) {
        this.region = region;
        this.passIndex = passIndex;
    }

    @Override
    public void setMeshes(int localSectionIndex, GlBufferSegment allocation, @Nullable GlBufferSegment indexAllocation, VertexRange[] ranges) {
        super.setMeshes(localSectionIndex, allocation, indexAllocation, ranges);
        updateSectionMesh(localSectionIndex);
    }

    @Override
    public void removeMeshes(int localSectionIndex) {
        super.removeMeshes(localSectionIndex);
        updateSectionMesh(localSectionIndex);
    }

    @Override
    public void replaceIndexBuffer(int localSectionIndex, GlBufferSegment indexAllocation) {
        super.replaceIndexBuffer(localSectionIndex, indexAllocation);
        updateSectionMesh(localSectionIndex);
    }

    @Override
    public void onBufferResized() {
        super.onBufferResized();
        for (int sectionIndex = 0; sectionIndex < 256; ++sectionIndex) {
            int sliceMask = SectionRenderDataUnsafe.getSliceMask(getDataPointer(sectionIndex));
            int facingCount = Integer.bitCount(sliceMask);
            sectionFacingCount[sectionIndex] = facingCount;
            totalFacingCount = Arrays.stream(sectionFacingCount).sum();
        }
        int regionIdx = MeshResource.MESH_MANAGER.indexOf(region);
        MeshResource.MESH_MANAGER.uploadRegionPassData(regionIdx, passIndex);
    }

    @Override
    public long getDataPointer(int sectionIndex) {
        return MeshResource.MESH_MANAGER.getSectionMemPointer(region, passIndex, sectionIndex);
    }

    @Override
    public void delete() {
        super.delete();
        MemoryUtil.memSet(getDataPointer(0), 0, MeshResource.MESH_MANAGER.getSectionSize());
        int regionIdx = MeshResource.MESH_MANAGER.indexOf(region);
        MeshResource.MESH_MANAGER.uploadRegionPassData(regionIdx, passIndex);
        totalFacingCount = 0;
    }

    protected void updateSectionMesh(int sectionIndex) {
        int regionIdx = MeshResource.MESH_MANAGER.indexOf(region);
        MeshResource.MESH_MANAGER.uploadSectionData(regionIdx, passIndex, sectionIndex);
        int sliceMask = SectionRenderDataUnsafe.getSliceMask(getDataPointer(sectionIndex));
        int facingCount = Integer.bitCount(sliceMask);
        sectionFacingCount[sectionIndex] = facingCount;
        totalFacingCount = Arrays.stream(sectionFacingCount).sum();
    }

    public int getTotalFacingCount() {
        return totalFacingCount;
    }
}