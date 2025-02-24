package rogo.sketchrender.mixin.sodium;

import me.jellysquid.mods.sodium.client.gl.device.GLRenderDevice;
import me.jellysquid.mods.sodium.client.gl.device.MultiDrawBatch;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlIndexType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.api.Config;
import rogo.sketchrender.api.TessellationDevice;
import rogo.sketchrender.shader.IndirectBuffer;

@Mixin(GLRenderDevice.class)
public class MixinGLRenderDevice implements TessellationDevice {
    @Shadow(remap = false)
    private GlTessellation activeTessellation;

    @Override
    public GlTessellation getTessellation() {
        return activeTessellation;
    }

    @Mixin(targets = "me.jellysquid.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList")
    public static class MixinImmediateDrawCommandList {
        private static final IndirectBuffer indirectBuffer = new IndirectBuffer(1792);

        public void fillIndirectBufferFromBatch(MultiDrawBatch batch, IndirectBuffer indirectBuffer) {
            // 获取批次的大小
            int size = batch.size;

            // 遍历每个绘制命令
            for (int i = 0; i < size; i++) {
                // 从内存中读取 baseVertex 和 elementCount
                int baseVertex = MemoryUtil.memGetInt(batch.pBaseVertex + (long) i * Integer.BYTES);
                int elementCount = MemoryUtil.memGetInt(batch.pElementCount + (long) i * Integer.BYTES);

                // 添加绘制命令到 IndirectBuffer
                indirectBuffer.addCommand(
                        elementCount, // count
                        1,           // instanceCount
                        0,  // firstIndex
                        baseVertex,  // baseVertex
                        0            // baseInstance
                );
            }
        }

        @Inject(method = "multiDrawElementsBaseVertex", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL32C;nglMultiDrawElementsBaseVertex(IJIJIJ)V"), remap = false, cancellable = true)
        private void onMultiDrawElementsBaseVertex(MultiDrawBatch batch, GlIndexType indexType, CallbackInfo ci) {
            if (Config.shouldComputeShader()) {
                ci.cancel();

                fillIndirectBufferFromBatch(batch, indirectBuffer);
                indirectBuffer.bind();
                GL46C.nglMultiDrawElementsIndirect(
                        4, // 图元类型
                        indexType.getFormatId(), // 索引类型
                        0,                      // 间接缓冲区的偏移量
                        indirectBuffer.getNumCommands(), // 绘制命令数量
                        0                       // 绘制命令之间的步长
                );
                indirectBuffer.unbind();
                indirectBuffer.refresh();
            }
        }
    }
}