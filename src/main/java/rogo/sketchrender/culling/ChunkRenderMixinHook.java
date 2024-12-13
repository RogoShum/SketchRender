package rogo.sketchrender.culling;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
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
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL46C;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.api.DataStorage;
import rogo.sketchrender.api.ExtraChunkRenderer;
import rogo.sketchrender.api.ExtraUniform;
import rogo.sketchrender.api.TessellationDevice;
import rogo.sketchrender.compat.sodium.ChunkShaderTracker;
import rogo.sketchrender.shader.IndirectCommandBuffer;
import rogo.sketchrender.shader.ShaderManager;

import java.util.Iterator;

import static org.lwjgl.opengl.GL42.GL_COMMAND_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;

public class ChunkRenderMixinHook {

    public static void onRenderStart(ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z, CallbackInfo ci) {
        SketchRender.TIMER.start("renderLayer");
    }

    public static void preRender(ChunkShaderInterface shader, ChunkRenderMatrices matrices, TerrainRenderPass pass) {
        IndirectCommandBuffer.INSTANCE.bind();
        ChunkCullingUniform.batchCommand.bindShaderSlot(4);
        ChunkCullingUniform.batchCounter.bindShaderSlot(5);
        ChunkCullingUniform.chunkData.bindShaderSlot(6);

        shader.setProjectionMatrix(matrices.projection());
        shader.setModelViewMatrix(matrices.modelView());

        int layer = 0;
        for (int i = 0; i < DefaultTerrainRenderPasses.ALL.length; ++i) {
            if (DefaultTerrainRenderPasses.ALL[layer] == pass) {
                layer = i;
            }
        }
        SketchRender.TIMER.start("collect_chunk");
        ShaderManager.COLLECT_CHUNK_CS.bindUniforms();
        ((ExtraUniform) ShaderManager.COLLECT_CHUNK_CS).getUniforms().setUniform("sketch_layer_pass", layer);
        SketchRender.TIMER.end("collect_chunk");
    }

    public static void preExecuteDrawBatch() {
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

    public static int maxElementCount = (SharedQuadIndexBuffer.IndexType.INTEGER.getMaxElementCount() / 4) - (16384 * 6) - 128;

    public static void onRender(ExtraChunkRenderer renderer, SharedQuadIndexBuffer sharedIndexBuffer, ChunkShaderInterface shader, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass pass, CameraTransform camera) {
        Iterator<ChunkRenderList> iterator = renderLists.iterator(pass.isReverseOrder());

        while (iterator.hasNext()) {
            ChunkRenderList renderList = iterator.next();
            RenderRegion region = renderList.getRegion();
            SectionRenderDataStorage storage = region.getStorage(pass);
            if (storage != null && region.getResources() != null) {
                IndirectCommandBuffer.INSTANCE.clear();
                IndirectCommandBuffer.INSTANCE.switchRegion(region.getChunkX(), region.getChunkY(), region.getChunkZ());
                ((DataStorage) storage).bindSSBO(3);
                ChunkRenderMixinHook.preExecuteDrawBatch();
                sharedIndexBuffer.ensureCapacity(commandList, maxElementCount);
                GlTessellation tessellation = renderer.sodiumTessellation(commandList, region);
                renderer.sodiumModelMatrixUniforms(shader, region, camera);
                DrawCommandList drawCommandList = commandList.beginTessellating(tessellation);

                try {
                    GlPrimitiveType primitiveType = ((TessellationDevice) GLRenderDevice.INSTANCE).getTessellation().getPrimitiveType();
                    GL46C.nglMultiDrawElementsIndirectCount(primitiveType.getId(), GlIndexType.UNSIGNED_INT.getFormatId(), 0, 0, 1792, 20);
                } catch (Throwable var7) {
                    if (drawCommandList != null) {
                        try {
                            drawCommandList.close();
                        } catch (Throwable var6) {
                            var7.addSuppressed(var6);
                        }
                    }

                    throw var7;
                }

                if (drawCommandList != null) {
                    drawCommandList.close();
                }
            }
        }
    }
}
