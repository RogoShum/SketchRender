package rogo.sketchrender.api.impl;

import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;

public interface IRenderChunkInfo {
    ChunkRenderDispatcher.RenderChunk getRenderChunk();

    int getStep();
}
