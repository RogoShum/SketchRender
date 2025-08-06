package rogo.sketchrender.minecraft;

import net.minecraft.client.Minecraft;
import rogo.sketchrender.util.ShaderPackLoader;

public class VanillaShaderPackLoader implements ShaderPackLoader {
    @Override
    public int getFrameBufferID() {
        return Minecraft.getInstance().getMainRenderTarget().frameBufferId;
    }

    @Override
    public boolean renderingShadowPass() {
        return false;
    }

    @Override
    public boolean enabledShader() {
        return false;
    }

    @Override
    public void bindDefaultFrameBuffer() {
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }
}