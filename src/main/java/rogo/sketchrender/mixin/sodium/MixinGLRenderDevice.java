package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.device.GLRenderDevice;
import me.jellysquid.mods.sodium.client.gl.device.MultiDrawBatch;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlIndexType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL43;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.api.TessellationDevice;
import rogo.sketchrender.shader.IndirectCommandBuffer;
import rogo.sketchrender.util.DrawElementsIndirectCommand;

@Mixin(GLRenderDevice.class)
public class MixinGLRenderDevice implements TessellationDevice {
    @Shadow(remap = false)
    private GlTessellation activeTessellation;

    @Override
    public GlTessellation getTessellation() {
        return activeTessellation;
    }


    @Mixin(targets = "me.jellysquid.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList")
    public abstract static class MixinDefaultChunkRenderer {

        @Inject(remap = false, method = "multiDrawElementsBaseVertex", at = @At(value = "HEAD"), cancellable = true)
        private void onExecuteDrawBatch(MultiDrawBatch batch, GlIndexType indexType, CallbackInfo ci) {
            if (Minecraft.getInstance().player != null && !Minecraft.getInstance().player.getOffhandItem().isEmpty()) {
                int drawCount = batch.size();
                int stride = DrawElementsIndirectCommand.SIZE;

                IndirectCommandBuffer.INSTANCE.bindOnce();
                IndirectCommandBuffer.INSTANCE.upload();
                GlPrimitiveType primitiveType = ((TessellationDevice) GLRenderDevice.INSTANCE).getTessellation().getPrimitiveType();
                GL43.glMultiDrawElementsIndirect(primitiveType.getId(), indexType.getFormatId(), 0, drawCount, stride);
                //IndirectCommandBuffer.INSTANCE.unBind();
                ci.cancel();
            }
        }
    }
}
