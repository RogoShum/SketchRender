package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.arena.GlBufferSegment;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.MultiDrawBatch;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.gl.util.VertexRange;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
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
import rogo.sketchrender.api.DataStorage;
import rogo.sketchrender.api.RenderSectionManagerSupplier;
import rogo.sketchrender.compat.sodium.RenderSectionManagerGetter;
import rogo.sketchrender.culling.ChunkDataStorage;
import rogo.sketchrender.culling.ChunkRenderMixinHook;
import rogo.sketchrender.shader.IndirectCommandBuffer;

@Mixin(SectionRenderDataStorage.class)
public class MixinSectionRenderDataStorage implements DataStorage {
    private RenderRegion region;
    private int terrainRenderPass;
    private int storageIndex = -1;

    @Inject(method = "setMeshes", at = @At(value = "HEAD"), remap = false)
    private void onSetMeshes(int localSectionIndex, GlBufferSegment allocation, VertexRange[] ranges, CallbackInfo ci) {
        ChunkDataStorage.sectionIndexTrace = localSectionIndex;
        ChunkDataStorage.dataStorageTrace = this;
    }

    @Inject(method = "setMeshes", at = @At(value = "RETURN"), remap = false)
    private void endSetMeshes(int localSectionIndex, GlBufferSegment allocation, VertexRange[] ranges, CallbackInfo ci) {
        ChunkDataStorage.sectionIndexTrace = -1;
        ChunkDataStorage.dataStorageTrace = null;
    }

    @Inject(method = "updateMeshes", at = @At(value = "HEAD"), remap = false)
    private void onUpdateMeshes(int sectionIndex, CallbackInfo ci) {
        ChunkDataStorage.sectionIndexTrace = sectionIndex;
        ChunkDataStorage.dataStorageTrace = this;
    }

    @Inject(method = "updateMeshes", at = @At(value = "RETURN"), remap = false)
    private void endUpdateMeshes(int sectionIndex, CallbackInfo ci) {
        ChunkDataStorage.sectionIndexTrace = -1;
        ChunkDataStorage.dataStorageTrace = null;
    }

    @Inject(method = "removeMeshes", at = @At(value = "RETURN"), remap = false)
    private void endRemoveMeshes(int localSectionIndex, CallbackInfo ci) {
        RenderSectionManagerGetter.getChunkData().removeSection(this, localSectionIndex);
    }

    @Inject(method = "delete", at = @At(value = "RETURN"), remap = false)
    private void endDelete(CallbackInfo ci) {
        RenderSectionManagerGetter.getChunkData().removeSectionStorage(this);
    }

    @Override
    public void setRenderRegion(RenderRegion renderRegion) {
        region = renderRegion;
    }

    @Override
    public void setTerrainPass(TerrainRenderPass terrainPass) {
        terrainRenderPass = ChunkDataStorage.PASS_INDEX_MAP.get(terrainPass);
    }

    @Override
    public void setStorageIndex(int storageIndex) {
        this.storageIndex = storageIndex;
    }

    @Override
    public RenderRegion getRenderRegion() {
        return region;
    }

    @Override
    public int getTerrainPass() {
        return terrainRenderPass;
    }

    @Override
    public int getStorageIndex() {
        return storageIndex;
    }
}
