package rogo.sketchrender.culling;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import org.lwjgl.opengl.GL30;

public class HizTarget extends RenderTarget {
    public HizTarget(int width, int height, boolean depth, boolean clear) {
        super(depth);
        this.resize(width, height, clear);
    }

    @Override
    public void createBuffers(int p_83951_, int p_83952_, boolean p_83953_) {
        super.createBuffers(p_83951_, p_83952_, p_83953_);
        this.colorTextureId = TextureUtil.generateTextureId();
        GlStateManager._bindTexture(this.colorTextureId);
        GlStateManager._texParameter(3553, 10242, 33071);
        GlStateManager._texParameter(3553, 10243, 33071);
        GlStateManager._texImage2D(3553, 0, GL30.GL_RGBA16F, this.width, this.height, 0, 6408, 5121, null);
        GlStateManager._glBindFramebuffer(36160, this.frameBufferId);
        GlStateManager._glFramebufferTexture2D(36160, 36064, 3553, this.colorTextureId, 0);
    }
}
