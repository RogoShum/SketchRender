package rogo.sketchrender.culling;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

public class HizTarget extends RenderTarget {
    private final boolean storeDepth;
    public HizTarget(int width, int height, boolean storeDepth) {
        super(false);
        this.storeDepth = storeDepth;
        this.resize(width, height, Minecraft.ON_OSX);
    }

    @Override
    public void createBuffers(int p_83951_, int p_83952_, boolean p_83953_) {
        super.createBuffers(p_83951_, p_83952_, p_83953_);
        this.colorTextureId = TextureUtil.generateTextureId();
        GlStateManager._bindTexture(this.colorTextureId);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        if (storeDepth) {
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R16F, this.width, this.height, 0, GL11.GL_RED, GL11.GL_FLOAT, null);
        } else {
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, this.width, this.height, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, null);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.frameBufferId);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, this.colorTextureId, 0);
    }
}
