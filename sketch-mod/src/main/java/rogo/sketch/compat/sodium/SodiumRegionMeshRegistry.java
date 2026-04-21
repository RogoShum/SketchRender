package rogo.sketch.compat.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import rogo.sketch.core.backend.BackendStorageBuffer;

/**
 * Sodium-only region mesh bridge kept on the mod side during the MeshResource
 * phase-A migration.
 */
public final class SodiumRegionMeshRegistry {
    private static final SodiumRegionMeshRegistry INSTANCE = new SodiumRegionMeshRegistry();

    private final RegionMeshManager regionMeshManager = new RegionMeshManager();

    private SodiumRegionMeshRegistry() {
    }

    public static SodiumRegionMeshRegistry getInstance() {
        return INSTANCE;
    }

    public int indexOf(RenderRegion region) {
        return regionMeshManager.indexOf(region);
    }

    public void attachMeshDataBuffer(BackendStorageBuffer meshDataBuffer) {
        regionMeshManager.attachMeshDataBuffer(meshDataBuffer);
    }

    public void addRegion(RenderRegion region) {
        regionMeshManager.addRegion(region);
    }

    public void removeRegion(RenderRegion region) {
        regionMeshManager.removeRegion(region);
    }

    public void clearRegions() {
        regionMeshManager.refresh();
    }

    public void initCapacity(int capacity) {
        regionMeshManager.initCapacity(capacity);
    }

    public int regionCount() {
        return regionMeshManager.regionCount();
    }

    public int regionCapacity() {
        return regionMeshManager.size();
    }

    public long getSectionMemPointer(RenderRegion region, int passIndex, int sectionIndex) {
        return regionMeshManager.getSectionMemPointer(region, passIndex, sectionIndex);
    }

    public void uploadSectionData(int regionIndex, int passIndex, int sectionIndex) {
        regionMeshManager.uploadSectionData(regionIndex, passIndex, sectionIndex);
    }

    public void uploadRegionPassData(int regionIndex, int passIndex) {
        regionMeshManager.uploadRegionPassData(regionIndex, passIndex);
    }

    public long getSectionSize() {
        return regionMeshManager.getSectionSize();
    }

    public BackendStorageBuffer meshDataBuffer() {
        return regionMeshManager.meshDataBuffer();
    }
}
