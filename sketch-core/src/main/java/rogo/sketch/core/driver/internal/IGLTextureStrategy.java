package rogo.sketch.core.driver.internal;

import java.nio.ByteBuffer;

/**
 * Strategy interface for OpenGL texture operations.
 * Implementations provide either DSA (Direct State Access) or Legacy approaches.
 */
public interface IGLTextureStrategy {
    
    /**
     * Create a new texture object
     * @param target The texture target (GL_TEXTURE_2D, GL_TEXTURE_3D, etc.)
     * @return The texture handle
     */
    int createTexture(int target);
    
    /**
     * Delete a texture object
     * @param id The texture handle
     */
    void deleteTexture(int id);
    
    /**
     * Upload 2D texture data
     * @param id The texture handle
     * @param level Mipmap level
     * @param internalFormat Internal format (GL_RGBA8, GL_RGB16F, etc.)
     * @param width Width in pixels
     * @param height Height in pixels
     * @param format Pixel data format (GL_RGBA, GL_RGB, etc.)
     * @param type Pixel data type (GL_UNSIGNED_BYTE, GL_FLOAT, etc.)
     * @param data Pixel data (can be null for allocation only)
     */
    void texImage2D(int id, int level, int internalFormat, int width, int height, 
                    int format, int type, ByteBuffer data);
    
    /**
     * Upload a sub-region of 2D texture data
     * @param id The texture handle
     * @param level Mipmap level
     * @param xOffset X offset in pixels
     * @param yOffset Y offset in pixels
     * @param width Width in pixels
     * @param height Height in pixels
     * @param format Pixel data format
     * @param type Pixel data type
     * @param data Pixel data
     */
    void texSubImage2D(int id, int level, int xOffset, int yOffset, int width, int height,
                       int format, int type, ByteBuffer data);
    
    /**
     * Upload 3D texture data
     * @param id The texture handle
     * @param level Mipmap level
     * @param internalFormat Internal format
     * @param width Width in pixels
     * @param height Height in pixels
     * @param depth Depth in pixels
     * @param format Pixel data format
     * @param type Pixel data type
     * @param data Pixel data (can be null)
     */
    void texImage3D(int id, int level, int internalFormat, int width, int height, int depth,
                    int format, int type, ByteBuffer data);
    
    /**
     * Set texture parameter (integer)
     * @param id The texture handle
     * @param pname Parameter name (GL_TEXTURE_MIN_FILTER, GL_TEXTURE_WRAP_S, etc.)
     * @param param Parameter value
     */
    void texParameteri(int id, int pname, int param);
    
    /**
     * Set texture parameter (float)
     * @param id The texture handle
     * @param pname Parameter name
     * @param param Parameter value
     */
    void texParameterf(int id, int pname, float param);
    
    /**
     * Generate mipmaps for a texture
     * @param id The texture handle
     */
    void generateMipmap(int id);
    
    /**
     * Bind a texture to the currently active texture unit
     * @param target The texture target
     * @param id The texture handle
     */
    void bindTexture(int target, int id);
    
    /**
     * Bind a texture to a specific texture unit (DSA style)
     * @param unit The texture unit (0, 1, 2, ...)
     * @param id The texture handle
     */
    void bindTextureUnit(int unit, int id);
    
    /**
     * Set the active texture unit
     * @param unit The texture unit (0, 1, 2, ...)
     */
    void activeTexture(int unit);
    
    /**
     * Bind texture as an image for compute shader access
     * @param unit Image unit
     * @param id Texture handle
     * @param level Mipmap level
     * @param layered Whether to bind all layers
     * @param layer Layer to bind (if not layered)
     * @param access Access mode (GL_READ_ONLY, GL_WRITE_ONLY, GL_READ_WRITE)
     * @param format Image format
     */
    void bindImageTexture(int unit, int id, int level, boolean layered, int layer, int access, int format);
    
    /**
     * Allocate immutable texture storage (GL 4.2+)
     * @param id The texture handle
     * @param levels Number of mipmap levels
     * @param internalFormat Internal format
     * @param width Width in pixels
     * @param height Height in pixels
     */
    void texStorage2D(int id, int levels, int internalFormat, int width, int height);
    
    /**
     * Allocate immutable 3D texture storage
     * @param id The texture handle
     * @param levels Number of mipmap levels
     * @param internalFormat Internal format
     * @param width Width in pixels
     * @param height Height in pixels
     * @param depth Depth in pixels
     */
    void texStorage3D(int id, int levels, int internalFormat, int width, int height, int depth);
}


