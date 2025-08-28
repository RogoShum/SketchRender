package rogo.sketch.util;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.pipeline.SodiumTerrainPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.Minecraft;
import rogo.sketch.mixin.iris.AccessorIrisRenderingPipeline;


public class IrisLoaderImpl implements ShaderPackLoader {
    @Override
    public int getFrameBufferID() {
        if (Iris.getPipelineManager().getPipeline().isPresent()) {
            WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipeline().get();

            if (pipeline instanceof AccessorIrisRenderingPipeline irisRenderingPipeline) {
                SodiumTerrainPipeline sodiumTerrainPipeline = irisRenderingPipeline.sodiumTerrainPipeline();
                GlFramebuffer glFramebuffer = sodiumTerrainPipeline.getTerrainSolidFramebuffer();
                return glFramebuffer.getId();
            }
        }

        return Minecraft.getInstance().getMainRenderTarget().frameBufferId;
    }

    @Override
    public boolean renderingShadowPass() {
        return IrisApi.getInstance().isRenderingShadowPass();
    }

    @Override
    public boolean enabledShader() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    @Override
    public void bindDefaultFrameBuffer() {
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }
}