package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.device.GLRenderDevice;
import me.jellysquid.mods.sodium.client.gl.device.MultiDrawBatch;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlIndexType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.api.TessellationDevice;
import rogo.sketchrender.util.DrawElementsIndirectCommand;
import rogo.sketchrender.util.SodiumMultiDrawHelper;

import java.nio.ByteBuffer;

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
                SodiumMultiDrawHelper helper = new SodiumMultiDrawHelper(batch);
                int drawCount = helper.getDrawCount();
                int stride = DrawElementsIndirectCommand.SIZE;

                ByteBuffer buffer = MemoryUtil.memAlloc(drawCount * stride);

                for (int i = 0; i < drawCount; i++) {
                    DrawElementsIndirectCommand command = helper.getDrawCommand(i);
                    buffer.putInt(command.count);
                    buffer.putInt(command.instanceCount);
                    buffer.putInt(command.firstIndex);
                    buffer.putInt(command.baseVertex);
                    buffer.putInt(command.baseInstance);
                }

                buffer.flip();
                int indirectBufferID = GL15.glGenBuffers();
                GL15.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, indirectBufferID);
                GL15.glBufferData(GL43.GL_DRAW_INDIRECT_BUFFER, buffer, GL15.GL_STATIC_DRAW);

                GlPrimitiveType primitiveType = ((TessellationDevice) GLRenderDevice.INSTANCE).getTessellation().getPrimitiveType();
                GL43.glMultiDrawElementsIndirect(primitiveType.getId(), indexType.getFormatId(), 0, drawCount, stride);

                MemoryUtil.memFree(buffer);
                GL15.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, 0);
                GL15.glDeleteBuffers(indirectBufferID);

                ci.cancel();
            }
        }
    }
}
