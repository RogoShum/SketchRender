package rogo.sketchrender.culling;

import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.GLRenderDevice;
import me.jellysquid.mods.sodium.client.gl.device.MultiDrawBatch;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlIndexType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.core.BlockPos;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL46C;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.api.Config;
import rogo.sketchrender.api.ExtraUniform;
import rogo.sketchrender.api.TessellationDevice;
import rogo.sketchrender.compat.sodium.ChunkShaderTracker;
import rogo.sketchrender.compat.sodium.RenderSectionManagerGetter;
import rogo.sketchrender.shader.IndirectCommandBuffer;
import rogo.sketchrender.shader.ShaderManager;

import static org.lwjgl.opengl.GL42.GL_COMMAND_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;

public class ChunkRenderMixinHook {

    public static void onRenderStart(ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z, CallbackInfo ci) {
        IndirectCommandBuffer.INSTANCE.bind();
        if (Config.getCullChunk()) {
            ChunkCullingUniform.batchCommand.bindShaderSlot(1);
            ChunkCullingUniform.batchCounter.bindShaderSlot(2);
            ChunkCullingUniform.chunkData.bindShaderSlot(3);
            RenderSectionManagerGetter.getChunkData().regionIndex.bindShaderSlot(4);

            SketchRender.TIMER.start("collect_chunk");
            RenderSectionManagerGetter.getChunkData().updateSSBO(ChunkCullingUniform.chunkData.getId());
            ShaderManager.COLLECT_CHUNK_CS.bindUniforms();
            for (int i = 0; i < DefaultTerrainRenderPasses.ALL.length; ++i) {
                if (pass == DefaultTerrainRenderPasses.ALL[i]) {
                    ((ExtraUniform) ShaderManager.COLLECT_CHUNK_CS).getUniforms().setUniform("sketch_layer_pass", i);
                }
            }
            SketchRender.TIMER.end("collect_chunk");
        }
        SketchRender.TIMER.start("renderLayer");
    }

    public static void onAddDrawCommands(MultiDrawBatch batch, long pMeshData, int mask, CallbackInfo ci) {
        if (Config.getCullChunk()) {
            ci.cancel();
            int size = batch.size;
            for (int facing = 0; facing < ModelQuadFacing.COUNT; ++facing) {
                size += mask >> facing & 1;
            }
            batch.size = size;
        }
    }

    public static void onExecuteDrawBatch(CommandList commandList, GlTessellation tessellation, MultiDrawBatch batch, CallbackInfo ci) {
        if (Config.getCullChunk()) {
            SketchRender.TIMER.start("CHUNK_CULLING_CS");
            ChunkCullingUniform.cullingCounter.updateCount(0);

            ShaderManager.COLLECT_CHUNK_CS.bind();
            BlockPos regionPos = new BlockPos(IndirectCommandBuffer.INSTANCE.getRegionPos());
            ((ExtraUniform) ShaderManager.COLLECT_CHUNK_CS).getUniforms().setUniform("sketch_region_pos", regionPos);
            ShaderManager.COLLECT_CHUNK_CS.execute(8, 4, 8);
            ShaderManager.COLLECT_CHUNK_CS.memoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT);

            GL20.glUseProgram(ChunkShaderTracker.lastProgram);
            SketchRender.TIMER.end("CHUNK_CULLING_CS");
        }
    }

    public static void onMultiDrawElementsBaseVertex(MultiDrawBatch batch, GlIndexType indexType, CallbackInfo ci) {
        if (Config.getCullChunk()) {
            GlPrimitiveType primitiveType = ((TessellationDevice) GLRenderDevice.INSTANCE).getTessellation().getPrimitiveType();
            GL46C.nglMultiDrawElementsIndirectCount(primitiveType.getId(), indexType.getFormatId(), 0, 0, 1792, 20);
            ci.cancel();
        }
    }
}
