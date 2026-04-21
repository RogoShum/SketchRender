package rogo.sketch.compat.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import rogo.sketch.mixin.sodium.AccessorRenderSectionManager;
import rogo.sketch.module.culling.TerrainMeshResourceSet;
import rogo.sketch.module.culling.TerrainRegionSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Terrain-only frame coordinator used during the culling migration.
 */
public final class SodiumTerrainCullCoordinator {
    private static final SodiumTerrainCullCoordinator INSTANCE = new SodiumTerrainCullCoordinator();

    private RenderSectionManager sectionManager;
    private List<RenderRegion> orderedRegions = List.of();
    private List<TerrainRegionSource> preparedSources = List.of();
    private int preparedFrame = Integer.MIN_VALUE;
    private boolean ready;

    private SodiumTerrainCullCoordinator() {
    }

    public static SodiumTerrainCullCoordinator getInstance() {
        return INSTANCE;
    }

    public void bind(RenderSectionManager renderSectionManager) {
        if (this.sectionManager != renderSectionManager) {
            this.sectionManager = renderSectionManager;
            invalidate();
        }
    }

    public void clear() {
        sectionManager = null;
        invalidate();
    }

    public List<TerrainRegionSource> prepareTerrainSources() {
        MeshResource.ensureInitialized();
        SodiumRegionMeshRegistry regionRegistry = MeshResource.regionRegistry();
        SortedRenderLists renderLists = currentRenderLists();
        if (renderLists == null) {
            invalidate();
            return List.of();
        }

        List<RenderRegion> nextOrderedRegions = collectRegions(renderLists);
        List<TerrainRegionSource> nextPreparedSources = new ArrayList<>(nextOrderedRegions.size());
        for (RenderRegion region : nextOrderedRegions) {
            nextPreparedSources.add(TerrainRegionSource.fromRegionKey(
                    region,
                    region.getChunkX(),
                    region.getChunkY(),
                    region.getChunkZ(),
                    region.getOriginX(),
                    region.getOriginY(),
                    region.getOriginZ(),
                    regionRegistry.indexOf(region)));
        }
        preparedSources = List.copyOf(nextPreparedSources);
        orderedRegions = List.of();
        preparedFrame = Integer.MIN_VALUE;
        ready = false;
        return preparedSources;
    }

    public boolean isReadyForFrame(int frame) {
        return ready && preparedFrame == frame;
    }

    public List<RenderRegion> orderedRegions() {
        return orderedRegions;
    }

    public List<TerrainRegionSource> preparedSources() {
        return preparedSources;
    }

    public void commitVisibleRegions(List<TerrainRegionSource> visibleTerrainRegions, int frame) {
        List<RenderRegion> nextOrderedRegions = new ArrayList<>();
        if (visibleTerrainRegions != null) {
            for (TerrainRegionSource source : visibleTerrainRegions) {
                if (source != null && source.hostKey() instanceof RenderRegion region) {
                    nextOrderedRegions.add(region);
                }
            }
        }
        orderedRegions = List.copyOf(nextOrderedRegions);
        MeshResource.resourceSet().setOrderedRegionSize(orderedRegions.size());
        preparedFrame = frame;
        ready = true;
    }

    private SortedRenderLists currentRenderLists() {
        if (sectionManager == null) {
            return null;
        }
        return ((AccessorRenderSectionManager) sectionManager).getRenderLists();
    }

    private List<RenderRegion> collectRegions(SortedRenderLists renderLists) {
        List<RenderRegion> regions = new ArrayList<>();
        Iterator<ChunkRenderList> iterator = renderLists.iterator(false);
        while (iterator.hasNext()) {
            RenderRegion region = iterator.next().getRegion();
            if (regionHasVisiblePass(region)) {
                regions.add(region);
            }
        }
        return regions;
    }

    private boolean regionHasVisiblePass(RenderRegion region) {
        for (TerrainRenderPass pass : DefaultTerrainRenderPasses.ALL) {
            SectionRenderDataStorage storage = region.getStorage(pass);
            if (storage != null && region.getResources() != null) {
                return true;
            }
        }
        return false;
    }

    private void invalidate() {
        orderedRegions = List.of();
        preparedSources = List.of();
        preparedFrame = Integer.MIN_VALUE;
        ready = false;
        MeshResource.resourceSet().setOrderedRegionSize(0);
    }
}
