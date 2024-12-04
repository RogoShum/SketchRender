package rogo.sketchrender.api;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public interface EntitiesForRender {
    ObjectArrayList<?> renderChunksInFrustum();
}
