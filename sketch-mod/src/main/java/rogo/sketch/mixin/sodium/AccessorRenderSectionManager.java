package rogo.sketch.mixin.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderSectionManager.class)
public interface AccessorRenderSectionManager {
    @Accessor(value = "renderLists", remap = false)
    SortedRenderLists getRenderLists();

    @Invoker(value = "getSearchDistance", remap = false)
    float invokeSearchDistance();

    @Invoker(value = "shouldUseOcclusionCulling", remap = false)
    boolean invokeShouldUseOcclusionCulling(Camera camera, boolean spectator);
}

