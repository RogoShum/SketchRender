package rogo.sketch.core.driver.internal;

/**
 * Strategy interface for OpenGL framebuffer operations.
 * Implementations provide either DSA (Direct State Access) or Legacy approaches.
 */
public interface IGLFramebufferStrategy {
    
    /**
     * Create a new framebuffer object
     * @return The framebuffer handle
     */
    int createFramebuffer();
    
    /**
     * Delete a framebuffer object
     * @param id The framebuffer handle
     */
    void deleteFramebuffer(int id);
    
    /**
     * Bind a framebuffer
     * @param target The framebuffer target (GL_FRAMEBUFFER, GL_READ_FRAMEBUFFER, GL_DRAW_FRAMEBUFFER)
     * @param id The framebuffer handle (0 for default framebuffer)
     */
    void bindFramebuffer(int target, int id);
    
    /**
     * Attach a texture to the framebuffer
     * @param framebuffer The framebuffer handle
     * @param attachment The attachment point (GL_COLOR_ATTACHMENT0, GL_DEPTH_ATTACHMENT, etc.)
     * @param textureTarget The texture target (GL_TEXTURE_2D, etc.)
     * @param texture The texture handle
     * @param level Mipmap level
     */
    void framebufferTexture2D(int framebuffer, int attachment, int textureTarget, int texture, int level);
    
    /**
     * Attach a texture layer to the framebuffer (for 3D/array textures)
     * @param framebuffer The framebuffer handle
     * @param attachment The attachment point
     * @param texture The texture handle
     * @param level Mipmap level
     * @param layer Layer index
     */
    void framebufferTextureLayer(int framebuffer, int attachment, int texture, int level, int layer);
    
    /**
     * Attach a renderbuffer to the framebuffer
     * @param framebuffer The framebuffer handle
     * @param attachment The attachment point
     * @param renderbufferTarget The renderbuffer target (GL_RENDERBUFFER)
     * @param renderbuffer The renderbuffer handle
     */
    void framebufferRenderbuffer(int framebuffer, int attachment, int renderbufferTarget, int renderbuffer);
    
    /**
     * Check framebuffer completeness
     * @param framebuffer The framebuffer handle
     * @param target The framebuffer target
     * @return Framebuffer status (GL_FRAMEBUFFER_COMPLETE if complete)
     */
    int checkFramebufferStatus(int framebuffer, int target);
    
    /**
     * Set draw buffers
     * @param framebuffer The framebuffer handle
     * @param buffers Array of draw buffer attachments
     */
    void drawBuffers(int framebuffer, int[] buffers);
    
    /**
     * Set read buffer
     * @param framebuffer The framebuffer handle
     * @param buffer The read buffer attachment
     */
    void readBuffer(int framebuffer, int buffer);
    
    /**
     * Blit framebuffer (copy pixels between framebuffers)
     * @param srcX0 Source region X start
     * @param srcY0 Source region Y start
     * @param srcX1 Source region X end
     * @param srcY1 Source region Y end
     * @param dstX0 Destination region X start
     * @param dstY0 Destination region Y start
     * @param dstX1 Destination region X end
     * @param dstY1 Destination region Y end
     * @param mask Buffers to copy (GL_COLOR_BUFFER_BIT, GL_DEPTH_BUFFER_BIT, GL_STENCIL_BUFFER_BIT)
     * @param filter Interpolation filter (GL_NEAREST, GL_LINEAR)
     */
    void blitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1,
                         int dstX0, int dstY0, int dstX1, int dstY1,
                         int mask, int filter);
    
    /**
     * Clear framebuffer color attachment
     * @param framebuffer The framebuffer handle
     * @param drawBuffer Draw buffer index
     * @param r Red value
     * @param g Green value
     * @param b Blue value
     * @param a Alpha value
     */
    void clearBufferfv(int framebuffer, int drawBuffer, float r, float g, float b, float a);
    
    /**
     * Clear framebuffer depth attachment
     * @param framebuffer The framebuffer handle
     * @param depth Depth value
     */
    void clearBufferDepth(int framebuffer, float depth);
    
    /**
     * Clear framebuffer stencil attachment
     * @param framebuffer The framebuffer handle
     * @param stencil Stencil value
     */
    void clearBufferStencil(int framebuffer, int stencil);
    
    // ==================== Renderbuffer Operations ====================
    
    /**
     * Create a new renderbuffer object
     * @return The renderbuffer handle
     */
    int createRenderbuffer();
    
    /**
     * Delete a renderbuffer object
     * @param id The renderbuffer handle
     */
    void deleteRenderbuffer(int id);
    
    /**
     * Bind a renderbuffer
     * @param target The renderbuffer target (GL_RENDERBUFFER)
     * @param id The renderbuffer handle
     */
    void bindRenderbuffer(int target, int id);
    
    /**
     * Allocate renderbuffer storage
     * @param renderbuffer The renderbuffer handle
     * @param internalFormat Internal format
     * @param width Width in pixels
     * @param height Height in pixels
     */
    void renderbufferStorage(int renderbuffer, int internalFormat, int width, int height);
    
    /**
     * Allocate multisample renderbuffer storage
     * @param renderbuffer The renderbuffer handle
     * @param samples Number of samples
     * @param internalFormat Internal format
     * @param width Width in pixels
     * @param height Height in pixels
     */
    void renderbufferStorageMultisample(int renderbuffer, int samples, int internalFormat, int width, int height);
}


