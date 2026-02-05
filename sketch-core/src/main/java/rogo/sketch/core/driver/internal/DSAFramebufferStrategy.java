package rogo.sketch.core.driver.internal;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;

/**
 * DSA (Direct State Access) implementation of framebuffer operations.
 * Uses OpenGL 4.5+ DSA functions for state-less framebuffer manipulation.
 */
public class DSAFramebufferStrategy implements IGLFramebufferStrategy {
    
    @Override
    public int createFramebuffer() {
        return GL45.glCreateFramebuffers();
    }
    
    @Override
    public void deleteFramebuffer(int id) {
        GL30.glDeleteFramebuffers(id);
    }
    
    @Override
    public void bindFramebuffer(int target, int id) {
        // Binding is still required for draw operations
        GL30.glBindFramebuffer(target, id);
    }
    
    @Override
    public void framebufferTexture2D(int framebuffer, int attachment, int textureTarget, int texture, int level) {
        GL45.glNamedFramebufferTexture(framebuffer, attachment, texture, level);
    }
    
    @Override
    public void framebufferTextureLayer(int framebuffer, int attachment, int texture, int level, int layer) {
        GL45.glNamedFramebufferTextureLayer(framebuffer, attachment, texture, level, layer);
    }
    
    @Override
    public void framebufferRenderbuffer(int framebuffer, int attachment, int renderbufferTarget, int renderbuffer) {
        GL45.glNamedFramebufferRenderbuffer(framebuffer, attachment, renderbufferTarget, renderbuffer);
    }
    
    @Override
    public int checkFramebufferStatus(int framebuffer, int target) {
        return GL45.glCheckNamedFramebufferStatus(framebuffer, target);
    }
    
    @Override
    public void drawBuffers(int framebuffer, int[] buffers) {
        GL45.glNamedFramebufferDrawBuffers(framebuffer, buffers);
    }
    
    @Override
    public void readBuffer(int framebuffer, int buffer) {
        GL45.glNamedFramebufferReadBuffer(framebuffer, buffer);
    }
    
    @Override
    public void blitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1,
                                 int dstX0, int dstY0, int dstX1, int dstY1,
                                 int mask, int filter) {
        // Note: DSA blit requires both FBOs to be named, which complicates things
        // For simplicity, use the standard blit which requires read/draw FBOs to be bound
        GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    @Override
    public void clearBufferfv(int framebuffer, int drawBuffer, float r, float g, float b, float a) {
        GL45.glClearNamedFramebufferfv(framebuffer, GL11.GL_COLOR, drawBuffer, new float[]{r, g, b, a});
    }
    
    @Override
    public void clearBufferDepth(int framebuffer, float depth) {
        GL45.glClearNamedFramebufferfv(framebuffer, GL11.GL_DEPTH, 0, new float[]{depth});
    }
    
    @Override
    public void clearBufferStencil(int framebuffer, int stencil) {
        GL45.glClearNamedFramebufferiv(framebuffer, GL11.GL_STENCIL, 0, new int[]{stencil});
    }
    
    // ==================== Renderbuffer Operations ====================
    
    @Override
    public int createRenderbuffer() {
        return GL45.glCreateRenderbuffers();
    }
    
    @Override
    public void deleteRenderbuffer(int id) {
        GL30.glDeleteRenderbuffers(id);
    }
    
    @Override
    public void bindRenderbuffer(int target, int id) {
        // Still needed for some legacy operations
        GL30.glBindRenderbuffer(target, id);
    }
    
    @Override
    public void renderbufferStorage(int renderbuffer, int internalFormat, int width, int height) {
        GL45.glNamedRenderbufferStorage(renderbuffer, internalFormat, width, height);
    }
    
    @Override
    public void renderbufferStorageMultisample(int renderbuffer, int samples, int internalFormat, int width, int height) {
        GL45.glNamedRenderbufferStorageMultisample(renderbuffer, samples, internalFormat, width, height);
    }
}


