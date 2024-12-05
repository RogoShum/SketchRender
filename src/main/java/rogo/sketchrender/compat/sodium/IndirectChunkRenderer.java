package rogo.sketchrender.compat.sodium;

import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;

public class IndirectChunkRenderer extends DefaultChunkRenderer {
    public IndirectChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        super(device, vertexType);
    }
}
