package rogo.sketchrender.compat.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import rogo.sketchrender.shader.uniform.SSBO;

public interface SectionData {
    void setMeshData(SSBO meshData, RenderRegion region, int pass);

    int facingCount();
}
