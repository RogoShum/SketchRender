package rogo.sketchrender.api;

import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import rogo.sketchrender.shader.uniform.SSBO;

public interface SectionData {
    void setMeshData(SSBO meshData, RenderRegion region, int pass);

    void bindMeshData(int slot);
    void bindCounter(int slot);
    void bindIndirectCommand(int slot);
    void bindCommandBuffer();
    void bindCounterBuffer();

    void clearCounter();
}
