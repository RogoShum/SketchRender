package rogo.sketchrender.api;

import net.minecraft.client.Minecraft;
import rogo.sketchrender.util.ShaderLoader;

public class DefaultShaderLoader implements ShaderLoader {
    @Override
    public int getFrameBufferID() {
        return Minecraft.getInstance().getMainRenderTarget().frameBufferId;
    }

    @Override
    public boolean renderingShaderPass() {
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
