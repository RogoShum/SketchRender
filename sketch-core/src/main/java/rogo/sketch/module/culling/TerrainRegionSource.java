package rogo.sketch.module.culling;

import org.joml.primitives.AABBf;

/**
 * Host-provided terrain region candidate consumed by the core culling path.
 * Stage 6 keeps this at region granularity; section granularity remains
 * deferred.
 */
public record TerrainRegionSource(
        Object hostKey,
        int chunkX,
        int chunkY,
        int chunkZ,
        int registryIndex,
        AABBf bounds) {
    public static final int REGION_SECTIONS_X = 8;
    public static final int REGION_SECTIONS_Y = 4;
    public static final int REGION_SECTIONS_Z = 8;
    public static final int SECTION_SIZE_BLOCKS = 16;
    public static final int REGION_SIZE_BLOCKS_X = REGION_SECTIONS_X * SECTION_SIZE_BLOCKS;
    public static final int REGION_SIZE_BLOCKS_Y = REGION_SECTIONS_Y * SECTION_SIZE_BLOCKS;
    public static final int REGION_SIZE_BLOCKS_Z = REGION_SECTIONS_Z * SECTION_SIZE_BLOCKS;

    public static TerrainRegionSource fromRegionKey(
            Object hostKey,
            int chunkX,
            int chunkY,
            int chunkZ,
            int originX,
            int originY,
            int originZ,
            int registryIndex) {
        float minX = originX;
        float minY = originY;
        float minZ = originZ;
        return new TerrainRegionSource(
                hostKey,
                chunkX,
                chunkY,
                chunkZ,
                registryIndex,
                new AABBf(
                        minX,
                        minY,
                        minZ,
                        minX + REGION_SIZE_BLOCKS_X,
                        minY + REGION_SIZE_BLOCKS_Y,
                        minZ + REGION_SIZE_BLOCKS_Z));
    }
}
