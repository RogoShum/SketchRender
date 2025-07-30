package rogo.sketchrender.compat.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import org.lwjgl.system.MemoryUtil;
import rogo.sketchrender.shader.uniform.SSBO;

import java.util.Arrays;

public class SectionDataUtil {

    public static int setMeshes(long pMeshDataArray, int localSectionIndex, SSBO meshData, int indexOffset, int[] sectionFacingCount, int passIndex, RenderRegion region, long passOffset) {
        copySectionMesh(pMeshDataArray, localSectionIndex, meshData, passIndex, region, passOffset, indexOffset);

        long pMeshData = getDataPointer(pMeshDataArray, localSectionIndex);
        int sliceMask = SectionRenderDataUnsafe.getSliceMask(pMeshData);
        int facingCount = Integer.bitCount(sliceMask);
        sectionFacingCount[localSectionIndex] = facingCount;
        return Arrays.stream(sectionFacingCount).sum();
    }

    public static void copySectionMesh(long pMeshDataArray, int index, SSBO meshData, int passIndex, RenderRegion region, long passOffset, int indexOffset) {
        MemoryUtil.memCopy(pMeshDataArray + RegionMeshManager.SECTION_DATA_SIZE * index, meshData.getMemoryAddress() + passOffset + (RegionMeshManager.SECTION_DATA_SIZE * index), RegionMeshManager.SECTION_DATA_SIZE);
        MeshUniform.meshManager.copySectionData(region, passIndex, index, pMeshDataArray + RegionMeshManager.SECTION_DATA_SIZE * index);
        meshData.upload(index + indexOffset);
    }

    private static long getDataPointer(long pMeshDataArray, int sectionIndex) {
        return SectionRenderDataUnsafe.heapPointer(pMeshDataArray, sectionIndex);
    }
}