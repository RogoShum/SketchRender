package rogo.sketchrender.api;

import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;

public interface DataStorage {
    void bindSSBO(int slot);
}
