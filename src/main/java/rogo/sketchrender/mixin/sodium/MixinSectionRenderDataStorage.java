package rogo.sketchrender.mixin.sodium;

import com.mojang.blaze3d.vertex.VertexFormatElement;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferSegment;
import me.jellysquid.mods.sodium.client.gl.util.VertexRange;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import org.lwjgl.opengl.GL15;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.api.DataStorage;
import rogo.sketchrender.shader.IndirectCommandBuffer;
import rogo.sketchrender.shader.uniform.CountBuffer;
import rogo.sketchrender.shader.uniform.SSBO;

@Mixin(SectionRenderDataStorage.class)
public class MixinSectionRenderDataStorage implements DataStorage {

    @Shadow
    @Final
    private long pMeshDataArray;

    private IndirectCommandBuffer drawCommandBuffer = new IndirectCommandBuffer(IndirectCommandBuffer.REGION_COMMAND_SIZE);
    private CountBuffer drawCounter = new CountBuffer(VertexFormatElement.Type.INT);
    private SSBO counterCommand = new SSBO(drawCounter);
    private SSBO batchCommand = new SSBO(drawCommandBuffer);
    private SSBO meshData;

    @Inject(method = "<init>", at = @At(value = "RETURN"), remap = false)
    private void onInit(CallbackInfo ci) {
        meshData = new SSBO(256, 64, this.pMeshDataArray, GL15.GL_STATIC_DRAW);
    }

    @Inject(method = "setMeshes", at = @At(value = "RETURN"), remap = false)
    private void endSetMeshes(int localSectionIndex, GlBufferSegment allocation, VertexRange[] ranges, CallbackInfo ci) {
        meshData.upload(localSectionIndex);
    }

    @Inject(method = "updateMeshes", at = @At(value = "RETURN"), remap = false)
    private void endSetMeshes(int sectionIndex, CallbackInfo ci) {
        meshData.upload(sectionIndex);
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
        meshData.upload(localSectionIndex);
    }

    @Inject(method = "delete", at = @At(value = "RETURN"), remap = false)
    private void endDelete(CallbackInfo ci) {
        meshData.discardBufferId();
        counterCommand.discard();
        batchCommand.discard();
    }
}
