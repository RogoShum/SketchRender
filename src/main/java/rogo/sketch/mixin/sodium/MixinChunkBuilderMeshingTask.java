package rogo.sketch.mixin.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
public class MixinChunkBuilderMeshingTask {


}