package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.arena.GlBufferSegment;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.MultiDrawBatch;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.gl.util.VertexRange;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import me.jellysquid.mods.sodium.client.util.iterator.ByteIterator;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
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
import rogo.sketchrender.shader.uniform.SSBO;

import java.nio.IntBuffer;

@Mixin(SectionRenderDataStorage.class)
public abstract class MixinSectionRenderDataStorage implements DataStorage {
    @Shadow @Final private GlBufferSegment[] allocations;
    @Shadow public abstract long getDataPointer(int sectionIndex);
    private SSBO meshSSBO = new SSBO(256, 64);
    private RenderRegion region;
    private int terrainRenderPass;
    private int storageIndex = -1;

    @Inject(method = "<init>", at = @At(value = "RETURN"), remap = false)
    private void onInit(CallbackInfo ci) {
        for (int i = 0; i < meshSSBO.getSize(); i++) {
            MemoryUtil.memPutInt(meshSSBO.getMemoryAddress() + i, 0);
        }
    }

    @Inject(method = "setMeshes", at = @At(value = "RETURN"), remap = false)
    private void endSetMeshes(int localSectionIndex, GlBufferSegment allocation, VertexRange[] ranges, CallbackInfo ci) {
        this.allocations[localSectionIndex] = allocation;
        long pMeshData = this.getDataPointer(localSectionIndex);
        int sliceMask = 0;
        int vertexOffset = allocation.getOffset();

        ChunkDataStorage.sectionIndexTrace = localSectionIndex;
        ChunkDataStorage.dataStorageTrace = this;
        int meshOffset = localSectionIndex * meshSSBO.getStride();
        for(int facingIndex = 0; facingIndex < ModelQuadFacing.COUNT; ++facingIndex) {
            VertexRange vertexRange = ranges[facingIndex];
            int vertexCount;
            if (vertexRange != null) {
                vertexCount = vertexRange.vertexCount();
            } else {
                vertexCount = 0;
            }

            MemoryUtil.memPutInt(meshSSBO.getMemoryAddress() + meshOffset + 8 + facingIndex * 8L, vertexOffset);
            MemoryUtil.memPutInt(meshSSBO.getMemoryAddress() + meshOffset + 12 + facingIndex * 8L, (vertexCount >> 2) * 6);
            RenderSectionManagerGetter.getChunkData().setVertexOffset(facingIndex, vertexOffset);
            RenderSectionManagerGetter.getChunkData().setElementCount(facingIndex, (vertexCount >> 2) * 6);
            if (vertexCount > 0) {
                sliceMask |= 1 << facingIndex;
            }

            vertexOffset += vertexCount;
        }
        MemoryUtil.memPutInt(meshSSBO.getMemoryAddress() + meshOffset, sliceMask);
        //MemoryUtil.memPutInt(meshSSBO.getMemoryAddress() + meshOffset + 4, 1);
        RenderSectionManagerGetter.getChunkData().setSliceMask(sliceMask);
        ChunkDataStorage.sectionIndexTrace = -1;
        ChunkDataStorage.dataStorageTrace = null;
        meshSSBO.upload(localSectionIndex);
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    private void updateMeshes(int sectionIndex) {
        ChunkDataStorage.sectionIndexTrace = sectionIndex;
        ChunkDataStorage.dataStorageTrace = this;
        int meshOffset = sectionIndex * meshSSBO.getStride();
        GlBufferSegment allocation = this.allocations[sectionIndex];
        if (allocation != null) {
            int offset = allocation.getOffset();
            long data = this.getDataPointer(sectionIndex);

            for(int facing = 0; facing < ModelQuadFacing.COUNT; ++facing) {
                SectionRenderDataUnsafe.setVertexOffset(data, facing, offset);
                MemoryUtil.memPutInt(meshSSBO.getMemoryAddress() + meshOffset + 8 + facing * 8L, offset);
                RenderSectionManagerGetter.getChunkData().setVertexOffset(facing, offset);
                int count = SectionRenderDataUnsafe.getElementCount(data, facing);
                offset += count / 6 * 4;
            }

        }
        meshSSBO.upload(sectionIndex);
        ChunkDataStorage.sectionIndexTrace = -1;
        ChunkDataStorage.dataStorageTrace = null;
    }

    @Override
    public void bindSSBO(int slot) {
        this.meshSSBO.bindShaderSlot(slot);
    }

    @Inject(method = "removeMeshes", at = @At(value = "RETURN"), remap = false)
    private void endRemoveMeshes(int localSectionIndex, CallbackInfo ci) {
        int meshOffset = localSectionIndex * meshSSBO.getStride();
        MemoryUtil.memPutInt(meshSSBO.getMemoryAddress() + meshOffset + 4, 0);
        meshSSBO.upload(localSectionIndex);
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
