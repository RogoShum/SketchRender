package rogo.sketchrender.compat.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.world.WorldRendererExtended;
import net.minecraft.client.Minecraft;
import rogo.sketchrender.api.ChunkDataStorageSupplier;
import rogo.sketchrender.api.RenderSectionManagerSupplier;
import rogo.sketchrender.culling.ChunkDataStorage;

import javax.annotation.Nullable;

public class RenderSectionManagerGetter {
    @Nullable
    public static RenderSectionManager get() {
        return ((RenderSectionManagerSupplier)((WorldRendererExtended)Minecraft.getInstance().levelRenderer).sodium$getWorldRenderer()).getRenderSectionManager();
    }

    public static ChunkDataStorage getChunkData() {
        return ((ChunkDataStorageSupplier)get()).getChunkDataStorage();
    }
}
