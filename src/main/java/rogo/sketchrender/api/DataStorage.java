package rogo.sketchrender.api;

import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;

public interface DataStorage {
    void setRenderRegion(RenderRegion renderRegion);
    void setTerrainPass(TerrainRenderPass terrainPass);
    void setStorageIndex(int storageIndex);

    RenderRegion getRenderRegion();
    int getTerrainPass();
    int getStorageIndex();
}
