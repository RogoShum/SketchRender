package rogo.sketchrender.mixin.sodium;

import com.mojang.blaze3d.vertex.VertexFormatElement;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferSegment;
import me.jellysquid.mods.sodium.client.gl.util.VertexRange;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.api.Config;
import rogo.sketchrender.api.SectionData;
import rogo.sketchrender.culling.ChunkCullingUniform;
import rogo.sketchrender.shader.IndirectCommandBuffer;
import rogo.sketchrender.shader.uniform.CountBuffer;
import rogo.sketchrender.shader.uniform.SSBO;

@Mixin(SectionRenderDataStorage.class)
public class MixinSectionRenderDataStorage implements SectionData {

    @Shadow
    @Final
    private long pMeshDataArray;

    private IndirectCommandBuffer drawCommandBuffer = new IndirectCommandBuffer(IndirectCommandBuffer.REGION_COMMAND_SIZE + 1);
    private CountBuffer drawCounter = new CountBuffer(VertexFormatElement.Type.INT);
    private SSBO counterCommand = new SSBO(drawCounter);
    private SSBO batchCommand = new SSBO(drawCommandBuffer);
    private SSBO meshData;
    private int passIndex;
    private long passOffset;
    private long regionOffset;
    private int indexOffset;
    private int regionIndexOffset;

    @Inject(method = "<init>", at = @At(value = "RETURN"), remap = false)
    private void afterInit(CallbackInfo ci) {

    }

    @Inject(method = "setMeshes", at = @At(value = "RETURN"), remap = false)
    private void endSetMeshes(int localSectionIndex, GlBufferSegment allocation, VertexRange[] ranges, CallbackInfo ci) {
        copySectionMesh(localSectionIndex);
        if (Config.getAutoDisableAsync()) {
            ChunkCullingUniform.batchMesh.upload(localSectionIndex + regionIndexOffset);
        } else {
            meshData.upload(localSectionIndex + indexOffset);
        }
    }

    @Inject(method = "updateMeshes", at = @At(value = "RETURN"), remap = false)
    private void endSetMeshes(int sectionIndex, CallbackInfo ci) {
        copySectionMesh(sectionIndex);
        if (Config.getAutoDisableAsync()) {
            ChunkCullingUniform.batchMesh.upload(sectionIndex + regionIndexOffset);
        } else {
            meshData.upload(sectionIndex + indexOffset);
        }
    }

    @Override
    public void setMeshData(SSBO meshData, int regionIndex, int passIndex) {
        this.meshData = meshData;

        this.passIndex = passIndex;
        this.passOffset = 256L * 64L * passIndex;
        this.indexOffset = 256 * passIndex;

        this.regionOffset = (regionIndex * 256L * 64L * 3) + this.passOffset;
        this.regionIndexOffset = (regionIndex * 256 * 3) + this.indexOffset;
    }

    @Override
    public void bindMeshData(int slot) {
        this.meshData.bindShaderSlot(slot);
    }

    @Override
    public void bindCounter(int slot) {
        this.counterCommand.bindShaderSlot(slot);
    }

    @Override
    public void bindIndirectCommand(int slot) {
        this.batchCommand.bindShaderSlot(slot);
    }

    @Override
    public void bindCommandBuffer() {
        drawCommandBuffer.bind();
    }

    @Override
    public void bindCounterBuffer() {
        drawCounter.bind();
    }

    @Override
    public void clearCounter() {
        drawCounter.updateCount(0);
    }

    @Inject(method = "removeMeshes", at = @At(value = "RETURN"), remap = false)
    private void endRemoveMeshes(int localSectionIndex, CallbackInfo ci) {
        copySectionMesh(localSectionIndex);
        if (Config.getAutoDisableAsync()) {
            ChunkCullingUniform.batchMesh.upload(localSectionIndex + regionIndexOffset);
        } else {
            meshData.upload(localSectionIndex + indexOffset);
        }
    }

    @Inject(method = "delete", at = @At(value = "RETURN"), remap = false)
    private void endDelete(CallbackInfo ci) {
        counterCommand.discard();
        batchCommand.discard();
    }

    @Unique
    private void copySectionMesh(int index) {
        if (Config.getAutoDisableAsync()) {
            MemoryUtil.memCopy(this.pMeshDataArray + 64L * index
                    , ChunkCullingUniform.batchMesh.getMemoryAddress() + this.regionOffset + (64L * index), 64L);
        } else {
            MemoryUtil.memCopy(this.pMeshDataArray + 64L * index, meshData.getMemoryAddress() + passOffset + (64L * index), 64L);
        }
    }
}
