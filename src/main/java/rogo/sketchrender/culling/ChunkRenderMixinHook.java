package rogo.sketchrender.culling;

import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.DrawCommandList;
import me.jellysquid.mods.sodium.client.gl.device.GLRenderDevice;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlIndexType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.SharedQuadIndexBuffer;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import net.minecraft.core.BlockPos;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL45;
import org.lwjgl.opengl.GL46C;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.api.SectionData;
import rogo.sketchrender.api.ExtraChunkRenderer;
import rogo.sketchrender.api.ExtraUniform;
import rogo.sketchrender.api.TessellationDevice;
import rogo.sketchrender.compat.sodium.ChunkShaderTracker;
import rogo.sketchrender.shader.IndirectCommandBuffer;
import rogo.sketchrender.shader.ShaderManager;

import java.nio.ByteBuffer;
import java.util.Iterator;

import static org.lwjgl.opengl.GL42.GL_COMMAND_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;

public class ChunkRenderMixinHook {

    public static void onRenderStart(ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z, CallbackInfo ci) {
        SketchRender.COMMAND_TIMER.start("renderLayer");
        //SketchRender.RENDER_TIMER.start("renderLayer_R");
    }

    public static void asyncReadInt(int ssboId, int offset, int[] outputBuffer) {
        GL45.glBindBuffer(GL45.GL_SHADER_STORAGE_BUFFER, ssboId);
        ByteBuffer dataBuffer = GL45.glMapBufferRange(GL45.GL_SHADER_STORAGE_BUFFER, offset, Integer.BYTES, GL45.GL_MAP_READ_BIT);

        if (dataBuffer != null) {
            outputBuffer[0] = dataBuffer.getInt(0);
            GL45.glUnmapBuffer(GL45.GL_SHADER_STORAGE_BUFFER);
        }
    }
}