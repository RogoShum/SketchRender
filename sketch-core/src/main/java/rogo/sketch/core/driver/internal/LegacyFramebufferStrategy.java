package rogo.sketch.core.driver.internal;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Legacy implementation of framebuffer operations.
 * Uses traditional bind-then-operate pattern with state save/restore.
 */
public class LegacyFramebufferStrategy implements IGLFramebufferStrategy {
    
    @Override
    public int createFramebuffer() {
        return GL30.glGenFramebuffers();
    }
    
    @Override
    public void deleteFramebuffer(int id) {
        GL30.glDeleteFramebuffers(id);
    }
    
    @Override
    public void bindFramebuffer(int target, int id) {
        GL30.glBindFramebuffer(target, id);
    }
    
    @Override
    public void framebufferTexture2D(int framebuffer, int attachment, int textureTarget, int texture, int level) {
        int previousBinding = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, attachment, textureTarget, texture, level);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousBinding);
    }
    
    @Override
    public void framebufferTextureLayer(int framebuffer, int attachment, int texture, int level, int layer) {
        int previousBinding = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, attachment, texture, level, layer);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousBinding);
    }
    
    @Override
    public void framebufferRenderbuffer(int framebuffer, int attachment, int renderbufferTarget, int renderbuffer) {
        int previousBinding = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, attachment, renderbufferTarget, renderbuffer);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousBinding);
    }
    
    @Override
    public int checkFramebufferStatus(int framebuffer, int target) {
        int previousBinding = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousBinding);
        return status;
    }
    
    @Override
    public void drawBuffers(int framebuffer, int[] buffers) {
        int previousBinding = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        GL30.glDrawBuffers(buffers);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousBinding);
    }
    
    @Override
    public void readBuffer(int framebuffer, int buffer) {
        int previousBinding = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
        GL11.glReadBuffer(buffer);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousBinding);
    }
    
    @Override
    public void blitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1,
                                 int dstX0, int dstY0, int dstX1, int dstY1,
                                 int mask, int filter) {
        // Requires read/draw framebuffers to already be bound
        GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    @Override
    public void clearBufferfv(int framebuffer, int drawBuffer, float r, float g, float b, float a) {
        int previousBinding = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        GL30.glClearBufferfv(GL11.GL_COLOR, drawBuffer, new float[]{r, g, b, a});
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousBinding);
    }
    
    @Override
    public void clearBufferDepth(int framebuffer, float depth) {
        int previousBinding = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        GL30.glClearBufferfv(GL11.GL_DEPTH, 0, new float[]{depth});
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousBinding);
    }
    
    @Override
    public void clearBufferStencil(int framebuffer, int stencil) {
        int previousBinding = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        GL30.glClearBufferiv(GL11.GL_STENCIL, 0, new int[]{stencil});
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousBinding);
    }
    
    // ==================== Renderbuffer Operations ====================
    
    @Override
    public int createRenderbuffer() {
        return GL30.glGenRenderbuffers();
    }
    
    @Override
    public void deleteRenderbuffer(int id) {
        GL30.glDeleteRenderbuffers(id);
    }
    
    @Override
    public void bindRenderbuffer(int target, int id) {
        GL30.glBindRenderbuffer(target, id);
    }
    
    @Override
    public void renderbufferStorage(int renderbuffer, int internalFormat, int width, int height) {
        int previousBinding = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, internalFormat, width, height);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousBinding);
    }
    
    @Override
    public void renderbufferStorageMultisample(int renderbuffer, int samples, int internalFormat, int width, int height) {
        int previousBinding = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
        GL30.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER, samples, internalFormat, width, height);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousBinding);
    }
}


