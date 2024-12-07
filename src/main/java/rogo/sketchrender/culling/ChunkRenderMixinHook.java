package rogo.sketchrender.culling;

import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.GLRenderDevice;
import me.jellysquid.mods.sodium.client.gl.device.MultiDrawBatch;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlIndexType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL46C;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.api.Config;
import rogo.sketchrender.api.TessellationDevice;
import rogo.sketchrender.compat.sodium.ChunkShaderTracker;
import rogo.sketchrender.shader.IndirectCommandBuffer;
import rogo.sketchrender.shader.ShaderManager;
import rogo.sketchrender.util.DrawElementsIndirectCommand;

import static org.lwjgl.opengl.GL42.GL_COMMAND_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;

public class ChunkRenderMixinHook {

    public static void onRenderStart(ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z, CallbackInfo ci) {
        IndirectCommandBuffer.INSTANCE.bind();
        if (Config.getCullChunk()) {
            ChunkCullingMessage.batchCulling.bindShaderSlot(0);
            ChunkCullingMessage.batchCommand.bindShaderSlot(1);
            ChunkCullingMessage.batchCounter.bindShaderSlot(2);
            ShaderManager.CHUNK_CULLING_CS.bindUniforms();
        }
        SketchRender.TIMER.start("renderLayer");
    }

    public static void onAddDrawCommands(MultiDrawBatch batch, long pMeshData, int mask, CallbackInfo ci) {
        if (Minecraft.getInstance().player != null && !Minecraft.getInstance().player.getOffhandItem().isEmpty()) {
            ci.cancel();

            int size = batch.size;
            if (Config.getCullChunk()) {
                for (int facing = 0; facing < ModelQuadFacing.COUNT; ++facing) {
                    IndirectCommandBuffer.INSTANCE.putSSBOData(
                            ChunkCullingMessage.batchCulling,
                            size,
                            SectionRenderDataUnsafe.getElementCount(pMeshData, facing),
                            SectionRenderDataUnsafe.getVertexOffset(pMeshData, facing));
                    size += mask >> facing & 1;
                }
            } else {
                for (int facing = 0; facing < ModelQuadFacing.COUNT; ++facing) {
                    IndirectCommandBuffer.INSTANCE.putChunkData(
                            size,
                            SectionRenderDataUnsafe.getElementCount(pMeshData, facing),
                            SectionRenderDataUnsafe.getVertexOffset(pMeshData, facing));
                    size += mask >> facing & 1;
                }
            }
            batch.size = size;
        }
    }

    public static void onExecuteDrawBatch(CommandList commandList, GlTessellation tessellation, MultiDrawBatch batch, CallbackInfo ci) {
        if (Minecraft.getInstance().player != null && !Minecraft.getInstance().player.getOffhandItem().isEmpty()) {
            int drawCount = batch.size();

            if (Config.getCullChunk()) {
                SketchRender.TIMER.start("CHUNK_CULLING_CS");
                ChunkCullingMessage.batchCulling.upload();
                ChunkCullingMessage.cullingCounter.updateCount(0);

                ShaderManager.CHUNK_CULLING_CS.bind();
                ShaderManager.CHUNK_CULLING_CS.execute(drawCount, 1, 1);
                ShaderManager.CHUNK_CULLING_CS.memoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT);

                GL20.glUseProgram(ChunkShaderTracker.lastProgram);
                SketchRender.TIMER.end("CHUNK_CULLING_CS");
            } else {
                SketchRender.TIMER.start("IndirectCommandBuffer");
                IndirectCommandBuffer.INSTANCE.upload();
                SketchRender.TIMER.end("IndirectCommandBuffer");
            }
        }
    }

    public static void onMultiDrawElementsBaseVertex(MultiDrawBatch batch, GlIndexType indexType, CallbackInfo ci) {
        if (Minecraft.getInstance().player != null && !Minecraft.getInstance().player.getOffhandItem().isEmpty()) {
            int drawCount = batch.size();
            int stride = DrawElementsIndirectCommand.SIZE;

            GlPrimitiveType primitiveType = ((TessellationDevice) GLRenderDevice.INSTANCE).getTessellation().getPrimitiveType();
            if (Config.getCullChunk()) {
                GL46C.nglMultiDrawElementsIndirectCount(primitiveType.getId(), indexType.getFormatId(), 0, 0, drawCount, stride);
            } else {
                GL46C.nglMultiDrawElementsIndirect(primitiveType.getId(), indexType.getFormatId(), 0, drawCount, stride);
            }
            ci.cancel();
        }
    }
}
