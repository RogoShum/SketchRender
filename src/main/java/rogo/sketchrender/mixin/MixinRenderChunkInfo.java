package rogo.sketchrender.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import rogo.sketchrender.api.RenderChunkInfo;

@Mixin(LevelRenderer.RenderChunkInfo.class)
public class MixinRenderChunkInfo implements RenderChunkInfo {

    @Final
    @Shadow
    ChunkRenderDispatcher.RenderChunk chunk;

    @Override
    public ChunkRenderDispatcher.RenderChunk getRenderChunk() {
        return chunk;
    }

    @Shadow
    @Final
    int step;

    @Override
    public int getStep() {
        return this.step;
    }
}