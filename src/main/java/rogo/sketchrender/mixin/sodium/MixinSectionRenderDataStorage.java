package rogo.sketchrender.mixin.sodium;

import com.mojang.blaze3d.vertex.VertexFormatElement;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferSegment;
import me.jellysquid.mods.sodium.client.gl.util.VertexRange;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.api.SectionData;
import rogo.sketchrender.culling.MeshUniform;
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
    private RenderRegion region;
    private int passIndex;
    private long passOffset;
    private int indexOffset;

    @Inject(method = "setMeshes", at = @At(value = "RETURN"), remap = false)
    private void endSetMeshes(int localSectionIndex, GlBufferSegment allocation, VertexRange[] ranges, CallbackInfo ci) {
        copySectionMesh(localSectionIndex);
        meshData.upload(localSectionIndex + indexOffset);
    }

    @Inject(method = "updateMeshes", at = @At(value = "RETURN"), remap = false)
    private void endSetMeshes(int sectionIndex, CallbackInfo ci) {
        copySectionMesh(sectionIndex);
        meshData.upload(sectionIndex + indexOffset);
    }

    @Override
    public void setMeshData(SSBO meshData, RenderRegion region, int passIndex) {
        this.meshData = meshData;
        this.region = region;

        this.passIndex = passIndex;
        this.passOffset = 256L * 64L * passIndex;
        this.indexOffset = 256 * passIndex;
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
        meshData.upload(localSectionIndex + indexOffset);
    }

    @Inject(method = "delete", at = @At(value = "RETURN"), remap = false)
    private void endDelete(CallbackInfo ci) {
        counterCommand.discard();
        batchCommand.discard();
    }

    @Unique
    private void copySectionMesh(int index) {
        MemoryUtil.memCopy(this.pMeshDataArray + 64L * index, meshData.getMemoryAddress() + passOffset + (64L * index), 64L);
        MeshUniform.meshManager.copySectionData(region, passIndex, index, this.pMeshDataArray + 64L * index);
    }
}
