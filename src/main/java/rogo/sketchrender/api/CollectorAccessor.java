package rogo.sketchrender.api;

import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;

public interface CollectorAccessor {
    void addAsyncToRebuildLists(RenderSection section);

    void addRenderList(ChunkRenderList renderList);
}