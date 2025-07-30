package rogo.sketchrender.culling;

import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.lwjgl.opengl.GL45;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.SketchRender;

import java.nio.ByteBuffer;

public class ChunkRenderMixinHook {
    public static void onRenderStart(ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z, CallbackInfo ci) {
        SketchRender.COMMAND_TIMER.start("renderLayer");
        //SketchRender.RENDER_TIMER.start("renderLayer_R");
    }
}