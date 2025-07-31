package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = SectionRenderDataStorage.class, remap = false)
public class MixinSectionRenderDataStorage {

}