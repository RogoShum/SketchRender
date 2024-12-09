package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.MultiDrawBatch;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import me.jellysquid.mods.sodium.client.util.iterator.ByteIterator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.compat.sodium.RenderSectionManagerGetter;
import rogo.sketchrender.culling.ChunkRenderMixinHook;
import rogo.sketchrender.shader.IndirectCommandBuffer;

@Mixin(DefaultChunkRenderer.class)
public abstract class MixinDefaultChunkRenderer extends ShaderChunkRenderer {

    public MixinDefaultChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        super(device, vertexType);
    }

    @Inject(method = "fillCommandBuffer", at = @At(value = "HEAD"), remap = false)
    private static void onFillCommandBufferStart(MultiDrawBatch batch, RenderRegion renderRegion, SectionRenderDataStorage renderDataStorage, ChunkRenderList renderList, CameraTransform camera, TerrainRenderPass pass, boolean useBlockFaceCulling, CallbackInfo ci) {
        IndirectCommandBuffer.INSTANCE.clear();
        IndirectCommandBuffer.INSTANCE.switchRegion(renderRegion.getChunkX(), renderRegion.getChunkY(), renderRegion.getChunkZ());
    }

    @Inject(method = "fillCommandBuffer", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/DefaultChunkRenderer;addDrawCommands(Lme/jellysquid/mods/sodium/client/gl/device/MultiDrawBatch;JI)V"), locals = LocalCapture.CAPTURE_FAILHARD, remap = false)
    private static void onFillCommandBuffer(MultiDrawBatch batch, RenderRegion renderRegion, SectionRenderDataStorage renderDataStorage, ChunkRenderList renderList, CameraTransform camera, TerrainRenderPass pass, boolean useBlockFaceCulling, CallbackInfo ci, ByteIterator iterator, int originX, int originY, int originZ, int sectionIndex, int chunkX, int chunkY, int chunkZ, long pMeshData, int slices) {
        IndirectCommandBuffer.INSTANCE.switchSection(chunkX, chunkY, chunkZ);
    }

    @Inject(method = "executeDrawBatch", at = @At(value = "HEAD"), remap = false)
    private static void onExecuteDrawBatch(CommandList commandList, GlTessellation tessellation, MultiDrawBatch batch, CallbackInfo ci) {
        SketchRender.TIMER.start("executeDrawBatch");
        ChunkRenderMixinHook.onExecuteDrawBatch(commandList, tessellation, batch, ci);
    }

    @Inject(method = "executeDrawBatch", at = @At(value = "RETURN"), remap = false)
    private static void endExecuteDrawBatch(CommandList commandList, GlTessellation tessellation, MultiDrawBatch batch, CallbackInfo ci) {
        SketchRender.TIMER.end("executeDrawBatch");
    }

    @Inject(method = "addDrawCommands", at = @At(value = "HEAD"), remap = false, cancellable = true)
    private static void onAddDrawCommands(MultiDrawBatch batch, long pMeshData, int mask, CallbackInfo ci) {
        ChunkRenderMixinHook.onAddDrawCommands(batch, pMeshData, mask, ci);
    }
}
