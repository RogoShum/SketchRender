package rogo.sketchrender.mixin.sodium;

import com.mojang.blaze3d.platform.GlStateManager;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.MultiDrawBatch;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import me.jellysquid.mods.sodium.client.util.iterator.ByteIterator;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import rogo.sketchrender.api.Config;
import rogo.sketchrender.compat.sodium.ChunkShaderTracker;
import rogo.sketchrender.culling.ChunkCullingMessage;
import rogo.sketchrender.shader.IndirectCommandBuffer;
import rogo.sketchrender.shader.ShaderManager;

import static org.lwjgl.opengl.GL42.GL_COMMAND_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;

@Mixin(DefaultChunkRenderer.class)
public abstract class MixinDefaultChunkRenderer extends ShaderChunkRenderer {

    public MixinDefaultChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        super(device, vertexType);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/shader/ChunkShaderInterface;setProjectionMatrix(Lorg/joml/Matrix4fc;)V"), remap = false)
    private void onRenderStart(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera, CallbackInfo ci) {
        ChunkShaderTracker.lastProgram = this.activeProgram.handle();
    }

    @Inject(method = "fillCommandBuffer", at = @At(value = "HEAD"), remap = false)
    private static void onFillCommandBufferStart(MultiDrawBatch batch, RenderRegion renderRegion, SectionRenderDataStorage renderDataStorage, ChunkRenderList renderList, CameraTransform camera, TerrainRenderPass pass, boolean useBlockFaceCulling, CallbackInfo ci) {
        IndirectCommandBuffer.INSTANCE.clear();
    }

    @Inject(method = "fillCommandBuffer", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/DefaultChunkRenderer;addDrawCommands(Lme/jellysquid/mods/sodium/client/gl/device/MultiDrawBatch;JI)V"), locals = LocalCapture.CAPTURE_FAILHARD, remap = false)
    private static void onFillCommandBuffer(MultiDrawBatch batch, RenderRegion renderRegion, SectionRenderDataStorage renderDataStorage, ChunkRenderList renderList, CameraTransform camera, TerrainRenderPass pass, boolean useBlockFaceCulling, CallbackInfo ci, ByteIterator iterator, int originX, int originY, int originZ, int sectionIndex, int chunkX, int chunkY, int chunkZ, long pMeshData, int slices) {
        IndirectCommandBuffer.INSTANCE.switchSection(chunkX, chunkY, chunkZ);
    }

    @Inject(method = "executeDrawBatch", at = @At(value = "HEAD"), remap = false)
    private static void onExecuteDrawBatch(CommandList commandList, GlTessellation tessellation, MultiDrawBatch batch, CallbackInfo ci) {
        if (Minecraft.getInstance().player != null && !Minecraft.getInstance().player.getOffhandItem().isEmpty()) {
            int drawCount = batch.size();

            if (Config.getCullChunk()) {
                ChunkCullingMessage.batchCulling.upload();

                ShaderManager.CHUNK_CULLING_CS.bind();
                ShaderManager.CHUNK_CULLING_CS.execute(drawCount, 1, 1);
                ShaderManager.CHUNK_CULLING_CS.memoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT);

                GL20.glUseProgram(ChunkShaderTracker.lastProgram);
            } else {
                IndirectCommandBuffer.INSTANCE.upload();
            }
        }
    }

    @Inject(method = "addDrawCommands", at = @At(value = "HEAD"), remap = false, cancellable = true)
    private static void onAddDrawCommands(MultiDrawBatch batch, long pMeshData, int mask, CallbackInfo ci) {
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
}
