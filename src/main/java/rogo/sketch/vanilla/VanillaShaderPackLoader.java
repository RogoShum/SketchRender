package rogo.sketch.vanilla;

import net.minecraft.client.Minecraft;
import rogo.sketch.util.ShaderPackLoader;

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