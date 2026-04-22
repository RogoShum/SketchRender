package rogo.sketch.module.culling;

import org.joml.Vector3i;
import org.lwjgl.system.MemoryUtil;
import rogo.sketch.core.backend.BackendStorageBuffer;

import java.util.List;

/**
 * Core culling-side occlusion dispatch helper for Hi-Z, terrain, and entity
 * owners. Stage 6 uses this to centralize dispatch readiness and sizing.
 */
public final class OcclusionSystem {
    private final HiZResourceProducer hiZResourceProducer;

    public OcclusionSystem(HiZResourceProducer hiZResourceProducer) {
        this.hiZResourceProducer = hiZResourceProducer;
    }

    public HiZResourceProducer hiZResourceProducer() {
        return hiZResourceProducer;
    }

    public boolean shouldRenderHiZ(CullingHostAdapter adapter, boolean anyCullingEnabled) {
        return anyCullingEnabled && hiZResourceProducer.hasActiveHiZInputs(adapter);
    }

    public int[] hiZDispatchGroups(CullingHostAdapter adapter, boolean firstPass) {
        return hiZResourceProducer.dispatchGroups(adapter, firstPass);
    }

    public boolean shouldRenderTerrain(
            CullingHostAdapter adapter,
            boolean chunkCullingEnabled,
            TerrainMeshResourceSet terrainResources) {
        return adapter != null
                && chunkCullingEnabled
                && adapter.terrainCullingHostActive()
                && terrainResources != null
                && terrainResources.orderedRegionSize() > 0;
    }

    public int terrainDispatchCount(TerrainMeshResourceSet terrainResources) {
        return terrainResources != null ? Math.max(terrainResources.orderedRegionSize(), 0) : 0;
    }

    public Vector3i[] hizDepthInfo(CullingHostAdapter adapter) {
        return hiZResourceProducer.depthInfo(adapter);
    }

    public List<TerrainRegionSource> buildVisibleTerrainInputs(
            List<TerrainRegionSource> visibleTerrainRegions,
            TerrainMeshResourceSet terrainResources) {
        if (terrainResources == null) {
            return List.of();
        }
        terrainResources.ensureCoreResources();
        List<TerrainRegionSource> visible = visibleTerrainRegions != null
                ? List.copyOf(visibleTerrainRegions)
                : List.of();

        BackendStorageBuffer regionIndexBuffer = terrainResources.regionIndexBuffer();
        if (regionIndexBuffer == null || regionIndexBuffer.isDisposed()) {
            terrainResources.setOrderedRegionSize(0);
            return List.of();
        }

        regionIndexBuffer.ensureCapacity(Math.max(visible.size(), 1), true);
        long pointer = regionIndexBuffer.memoryAddress();
        if (pointer != 0L) {
            for (int index = 0; index < visible.size(); ++index) {
                TerrainRegionSource region = visible.get(index);
                long offset = index * TerrainMeshResourceSet.REGION_INDEX_STRIDE_BYTES;
                MemoryUtil.memPutInt(pointer + offset, region.chunkX());
                MemoryUtil.memPutInt(pointer + offset + 4L, region.chunkY());
                MemoryUtil.memPutInt(pointer + offset + 8L, region.chunkZ());
                MemoryUtil.memPutInt(pointer + offset + 12L, region.registryIndex());
            }
        }

        long uploadBytes = visible.size() * TerrainMeshResourceSet.REGION_INDEX_STRIDE_BYTES;
        regionIndexBuffer.position(uploadBytes);
        if (uploadBytes > 0L) {
            regionIndexBuffer.upload();
        }

        terrainResources.setOrderedRegionSize(visible.size());
        if (terrainResources.cullingCounter() != null) {
            terrainResources.cullingCounter().updateCount(0);
        }
        if (terrainResources.elementCounter() != null) {
            terrainResources.elementCounter().updateCount(0);
        }
        return visible;
    }

    public void clearTerrainInputs(TerrainMeshResourceSet terrainResources) {
        if (terrainResources == null) {
            return;
        }
        terrainResources.setOrderedRegionSize(0);
        if (terrainResources.regionIndexBuffer() != null && !terrainResources.regionIndexBuffer().isDisposed()) {
            terrainResources.regionIndexBuffer().position(0L);
        }
        if (terrainResources.cullingCounter() != null) {
            terrainResources.cullingCounter().updateCount(0);
        }
        if (terrainResources.elementCounter() != null) {
            terrainResources.elementCounter().updateCount(0);
        }
    }
}
