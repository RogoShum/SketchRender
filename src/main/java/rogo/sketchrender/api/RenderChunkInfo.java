package rogo.sketchrender.api;

import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;

public interface RenderChunkInfo {
    ChunkRenderDispatcher.RenderChunk getRenderChunk();

    int getStep();
}
