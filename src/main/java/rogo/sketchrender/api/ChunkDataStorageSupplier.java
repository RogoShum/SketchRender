package rogo.sketchrender.api;

import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegionManager;
import rogo.sketchrender.culling.ChunkDataStorage;

public interface ChunkDataStorageSupplier {
    ChunkDataStorage getChunkDataStorage();
    RenderRegionManager getRenderRegionManager();
}
