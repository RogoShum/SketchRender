package rogo.sketch.compat.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import net.minecraft.client.Minecraft;
import rogo.sketch.Config;
import rogo.sketch.module.culling.TerrainMeshResourceSet;

/**
 * Compatibility facade during the MeshResource phase-B migration.
 * Terrain mesh resource ownership lives in {@link TerrainMeshResourceSet};
 * this class only bridges Sodium events and registry wiring into that owner.
 */
public final class MeshResource {
    private static final TerrainMeshResourceSet RESOURCE_SET = TerrainMeshResourceSet.getInstance();
    private static final SodiumRegionMeshRegistry REGION_REGISTRY = SodiumRegionMeshRegistry.getInstance();

    private MeshResource() {
    }

    public static void ensureInitialized() {
        RESOURCE_SET.ensureCoreResources();
        REGION_REGISTRY.attachMeshDataBuffer(
                RESOURCE_SET.ensureMeshDataBuffer(
                        RegionMeshManager.SECTION_DATA_SIZE,
                        Math.max(REGION_REGISTRY.regionCapacity(), 1)));
    }

    public static TerrainMeshResourceSet resourceSet() {
        ensureInitialized();
        return RESOURCE_SET;
    }

    public static SodiumRegionMeshRegistry regionRegistry() {
        ensureInitialized();
        return REGION_REGISTRY;
    }

    public static void addIndexedRegion(RenderRegion region) {
        ensureInitialized();
        REGION_REGISTRY.addRegion(region);

        if (Config.getCullChunk()) {
            RESOURCE_SET.onRegionCapacityChanged(REGION_REGISTRY.regionCapacity());
        }
    }

    public static void removeRegion(RenderRegion region) {
        ensureInitialized();
        REGION_REGISTRY.removeRegion(region);
    }

    public static void clearRegions() {
        ensureInitialized();
        REGION_REGISTRY.clearRegions();
        RESOURCE_SET.clearRegions();
    }

    public static void updateDistance(int renderDistance) {
        ensureInitialized();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        RESOURCE_SET.updateDistance(renderDistance, minecraft.level.getSectionsCount());
        if (Config.getCullChunk()) {
            REGION_REGISTRY.initCapacity(RESOURCE_SET.theoreticalRegionQuantity());
            RESOURCE_SET.onRegionCapacityChanged(REGION_REGISTRY.regionCapacity());
        }
    }

    public static int getRenderDistance() {
        return RESOURCE_SET.renderDistance();
    }

    public static int getSpacePartitionSize() {
        return RESOURCE_SET.spacePartitionSize();
    }
}
